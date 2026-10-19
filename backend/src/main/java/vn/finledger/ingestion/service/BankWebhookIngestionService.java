package vn.finledger.ingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.finledger.ingestion.domain.BankAccountMapping;
import vn.finledger.ingestion.domain.WebhookInboxLog;
import vn.finledger.ingestion.domain.WebhookInboxStatus;
import vn.finledger.ingestion.dto.BankWebhookPayload;
import vn.finledger.ingestion.dto.BankWebhookResponse;
import vn.finledger.ingestion.repository.BankAccountMappingRepository;
import vn.finledger.ingestion.repository.WebhookInboxLogRepository;
import vn.finledger.ingestion.security.PiiSanitizer;
import vn.finledger.ledger.dto.JournalEntryResponse;
import vn.finledger.ledger.dto.PostingLegRequest;
import vn.finledger.ledger.dto.RecordTransactionRequest;
import vn.finledger.ledger.service.LedgerService;

import vn.finledger.splitbill.domain.DebtSettlement;
import vn.finledger.splitbill.domain.SettlementStatus;
import vn.finledger.splitbill.repository.DebtSettlementRepository;
import vn.finledger.splitbill.service.GroupSplitService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankWebhookIngestionService {

    private final RedisIdempotencyService redisIdempotencyService;
    private final BankAccountMappingRepository bankAccountMappingRepository;
    private final TransactionCategorizerService transactionCategorizerService;
    private final LedgerService ledgerService;
    private final WebhookInboxLogRepository webhookInboxLogRepository;
    private final PiiSanitizer piiSanitizer;
    private final ObjectMapper objectMapper;
    private final GroupSplitService groupSplitService;
    private final DebtSettlementRepository debtSettlementRepository;

    private static final Pattern SETTLEMENT_CODE_PATTERN = Pattern.compile("FLV[A-Z0-9]{6}");

    /**
     * Ingests bank webhook payload with two-phase Redis idempotency,
     * PII sanitization, smart categorization, and double-entry ledger persistence.
     */
    public BankWebhookResponse processWebhook(String provider, BankWebhookPayload payload, String rawPayload) {
        String externalId = payload.getExternalId();
        String sanitizedPayload = piiSanitizer.sanitize(rawPayload != null ? rawPayload : payload.getContent());

        // 1. Two-Phase Redis Idempotency Check
        IdempotencyLockResult lockResult = redisIdempotencyService.tryAcquireLock(provider, externalId);
        if (!lockResult.isAcquired()) {
            log.warn("Webhook duplicate detected for provider={}, externalTxId={}. Safely returning DUPLICATE_IGNORED",
                    provider, externalId);
            saveInboxLog(provider, externalId, sanitizedPayload, WebhookInboxStatus.DUPLICATE,
                    lockResult.getExistingJournalEntryId(), "Duplicate transaction ignored by Redis idempotency");

            return BankWebhookResponse.builder()
                    .status("DUPLICATE_IGNORED")
                    .journalEntryId(lockResult.getExistingJournalEntryId())
                    .message("Duplicate webhook call safely ignored")
                    .build();
        }

        try {
            // 2. Lookup Bank Account Mapping
            BankAccountMapping mapping = bankAccountMappingRepository.findByBankAccountNumber(payload.getAccountNumber())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No ledger account mapping found for bank account: " + payload.getAccountNumber()));

            // 3. Categorize Counterparty Account
            String transferType = payload.getTransferType() != null ? payload.getTransferType().trim().toUpperCase() : "OUT";
            CategorizationResult catResult = transactionCategorizerService.categorize(
                    payload.getContent(), transferType, mapping);
            UUID counterpartyAccountId = catResult.getTargetAccountId();

            // 4. Construct Double-Entry Journal Legs
            BigDecimal amount = payload.getTransferAmount();
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Transfer amount must be strictly greater than zero");
            }

            // VietQR Auto-Settlement Check: match pending group debt settlements by settlement_code
            if ("IN".equalsIgnoreCase(transferType) && payload.getContent() != null) {
                Matcher matcher = SETTLEMENT_CODE_PATTERN.matcher(payload.getContent());
                if (matcher.find()) {
                    String code = matcher.group();
                    Optional<DebtSettlement> settlementOpt = debtSettlementRepository
                            .findBySettlementCodeAndStatus(code, SettlementStatus.PENDING);
                    if (settlementOpt.isPresent()) {
                        DebtSettlement settlement = settlementOpt.get();
                        BigDecimal expectedAmount = settlement.getAmount().setScale(4, RoundingMode.HALF_EVEN);
                        BigDecimal actualAmount = amount.setScale(4, RoundingMode.HALF_EVEN);
                        if (expectedAmount.compareTo(actualAmount) == 0) {
                            log.info("VietQR Auto-Settlement triggered for code={}, settlementId={}",
                                    code, settlement.getId());
                            groupSplitService.settleDebt(settlement.getId());
                        }
                    }
                }
            }

            BigDecimal bankDelta;
            BigDecimal counterpartyDelta;

            if ("IN".equalsIgnoreCase(transferType)) {
                // Money IN (Tiền vào): DEBIT Bank (+amount), CREDIT Revenue/Receivable (-amount)
                bankDelta = amount;
                counterpartyDelta = amount.negate();
            } else {
                // Money OUT (Tiền ra): CREDIT Bank (-amount), DEBIT Expense/Payable (+amount)
                bankDelta = amount.negate();
                counterpartyDelta = amount;
            }

            String referenceId = "WEBHOOK:" + provider.toUpperCase() + ":" + externalId;
            String description = piiSanitizer.sanitize(payload.getContent());
            if (description == null || description.isBlank()) {
                description = "Bank Webhook " + provider + " - " + externalId;
            }

            RecordTransactionRequest recordRequest = RecordTransactionRequest.builder()
                    .transactionDate(Instant.now())
                    .description(description)
                    .referenceId(referenceId)
                    .postingLegs(List.of(
                            PostingLegRequest.builder()
                                    .accountId(mapping.getLedgerAccountId())
                                    .amount(bankDelta)
                                    .build(),
                            PostingLegRequest.builder()
                                    .accountId(counterpartyAccountId)
                                    .amount(counterpartyDelta)
                                    .build()
                    ))
                    .build();

            // 5. Persist into Ledger Engine
            JournalEntryResponse journalEntry = ledgerService.recordTransaction(recordRequest);

            // 6. Two-Phase Commit: Mark Completed in Redis (24-hour TTL)
            redisIdempotencyService.markCompleted(provider, externalId, journalEntry.getId());

            // 7. Audit Log
            saveInboxLog(provider, externalId, sanitizedPayload, WebhookInboxStatus.PROCESSED,
                    journalEntry.getId(), null);

            log.info("Successfully ingested webhook provider={}, externalTxId={}, journalEntryId={}",
                    provider, externalId, journalEntry.getId());

            return BankWebhookResponse.builder()
                    .status("PROCESSED")
                    .journalEntryId(journalEntry.getId())
                    .message("Transaction processed and recorded into ledger successfully")
                    .build();

        } catch (Exception e) {
            log.error("Failed to process webhook for provider={}, externalTxId={}. Releasing Redis lock for retry.",
                    provider, externalId, e);
            redisIdempotencyService.releaseLockOnFailure(provider, externalId);
            saveInboxLog(provider, externalId, sanitizedPayload, WebhookInboxStatus.FAILED,
                    null, e.getMessage());
            throw new RuntimeException("Webhook ingestion failed: " + e.getMessage(), e);
        }
    }

    private void saveInboxLog(
            String provider,
            String externalId,
            String sanitizedPayload,
            WebhookInboxStatus status,
            UUID journalEntryId,
            String errorMessage) {
        try {
            WebhookInboxLog inboxLog = WebhookInboxLog.builder()
                    .provider(provider)
                    .externalTransactionId(externalId)
                    .sanitizedPayload(sanitizedPayload)
                    .status(status)
                    .journalEntryId(journalEntryId)
                    .errorMessage(errorMessage)
                    .receivedAt(Instant.now())
                    .build();
            webhookInboxLogRepository.save(inboxLog);
        } catch (Exception ex) {
            log.error("Failed to save webhook inbox audit log", ex);
        }
    }
}
