package vn.finledger.ingestion.dedup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.finledger.ingestion.domain.BankAccountMapping;
import vn.finledger.ingestion.domain.IngestionFingerprint;
import vn.finledger.ingestion.domain.WebhookInboxLog;
import vn.finledger.ingestion.domain.WebhookInboxStatus;
import vn.finledger.ingestion.parsers.ParsedBalanceEvent;
import vn.finledger.ingestion.parsers.TransferDirection;
import vn.finledger.ingestion.repository.BankAccountMappingRepository;
import vn.finledger.ingestion.repository.WebhookInboxLogRepository;
import vn.finledger.ingestion.security.PiiSanitizer;
import vn.finledger.ingestion.service.CategorizationResult;
import vn.finledger.ingestion.service.LocalTrainableCategorizer;
import vn.finledger.ledger.domain.Account;
import vn.finledger.ledger.dto.JournalEntryResponse;
import vn.finledger.ledger.dto.PostingLegRequest;
import vn.finledger.ledger.dto.RecordTransactionRequest;
import vn.finledger.ledger.repository.AccountRepository;
import vn.finledger.ledger.service.LedgerService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Runs the transactional ingestion workflow once distributed locks and parsing are complete.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiSourceTransactionalProcessor {

    private final BankAccountMappingRepository mappingRepository;
    private final MultiSourceFuzzyDeduplicator deduplicator;
    private final InternalTransferReconciler internalTransferReconciler;
    private final LocalTrainableCategorizer categorizer;
    private final LedgerService ledgerService;
    private final AccountRepository accountRepository;
    private final WebhookInboxLogRepository inboxLogRepository;
    private final PiiSanitizer piiSanitizer;

    @Transactional
    public MultiSourceIngestionResult processInTransaction(
            BankAccountMapping sourceMapping,
            ParsedBalanceEvent event,
            String rawMessage) {

        Instant now = Instant.now();
        UUID sourceAccountId = sourceMapping.getLedgerAccountId();
        BigDecimal exactAmount = event.amount().setScale(4, RoundingMode.HALF_EVEN);

        // Update reported bank/wallet balance telemetry on bank_account_mappings (mutable metadata table)
        if (event.runningBalanceAfterTx() != null) {
            sourceMapping.setLastReportedBalance(event.runningBalanceAfterTx().setScale(4, RoundingMode.HALF_EVEN));
        }
        sourceMapping.setLastSyncedAt(now);
        mappingRepository.save(sourceMapping);

        // 1. Multi-source fuzzy & exact-ref deduplication check on this account
        MultiSourceFuzzyDeduplicator.DedupCheckResult dedup =
                deduplicator.checkAndMergeIfDuplicate(sourceAccountId, event);
        if (dedup.duplicate()) {
            recordAuditLog(sourceMapping.getProviderCode(), event, rawMessage, WebhookInboxStatus.DUPLICATE, dedup.journalEntryId());
            BigDecimal ledgerBal = fetchCurrentLedgerBalance(sourceAccountId);
            return MultiSourceIngestionResult.builder()
                    .status("MERGED_DUPLICATE")
                    .providerCode(sourceMapping.getProviderCode())
                    .channel(event.channel().fingerprintTag())
                    .accountIdentifier(sourceMapping.getAccountIdentifier())
                    .transferDirection(event.transferDirection().name())
                    .amount(exactAmount)
                    .runningBalanceAfterTx(sourceMapping.getLastReportedBalance())
                    .ledgerBalanceAfterTx(ledgerBal)
                    .extractedReferenceCode(event.extractedReferenceCode())
                    .counterpartyHint(event.counterpartyHint())
                    .cleanDescription(event.rawDescription())
                    .sourceAccountId(sourceAccountId)
                    .journalEntryId(dedup.journalEntryId())
                    .sourcesMerged(dedup.sourcesMerged())
                    .syncedAt(now)
                    .pipelineTrace("DEDUP_MERGED[" + dedup.sourcesMerged() + "]")
                    .build();
        }

        // 2. Auto Internal Transfer Reconciliation (VCB <-> MoMo)
        InternalTransferReconciler.InternalReconciliationResult internalResult =
                internalTransferReconciler.reconcileIfInternalTransfer(sourceMapping, event);
        if (internalResult.handled()) {
            WebhookInboxStatus auditStatus = "INTERNAL_TRANSFER_LINKED".equals(internalResult.status())
                    ? WebhookInboxStatus.DUPLICATE
                    : WebhookInboxStatus.PROCESSED;
            recordAuditLog(sourceMapping.getProviderCode(), event, rawMessage, auditStatus, internalResult.journalEntryId());
            BigDecimal ledgerBal = fetchCurrentLedgerBalance(sourceAccountId);

            return MultiSourceIngestionResult.builder()
                    .status(internalResult.status())
                    .providerCode(sourceMapping.getProviderCode())
                    .channel(event.channel().fingerprintTag())
                    .accountIdentifier(sourceMapping.getAccountIdentifier())
                    .transferDirection(event.transferDirection().name())
                    .amount(exactAmount)
                    .runningBalanceAfterTx(sourceMapping.getLastReportedBalance())
                    .ledgerBalanceAfterTx(ledgerBal)
                    .extractedReferenceCode(event.extractedReferenceCode())
                    .counterpartyHint(internalResult.counterpartProvider())
                    .cleanDescription(event.rawDescription())
                    .sourceAccountId(sourceAccountId)
                    .targetCategoryAccountId(internalResult.counterpartAccountId())
                    .targetCategoryName("Chuyển nội bộ " + sourceMapping.getProviderCode() + " <-> " + internalResult.counterpartProvider())
                    .categorizationSource("INTERNAL_TRANSFER_RECONCILER")
                    .journalEntryId(internalResult.journalEntryId())
                    .sourcesMerged(internalResult.sourcesMerged())
                    .syncedAt(now)
                    .pipelineTrace(internalResult.status() + "[EXPENSE=0,REVENUE=0]")
                    .build();
        }

        // 3. Standard External Income / Expense Double-Entry Recording with LocalTrainableCategorizer
        CategorizationResult catResult = categorizer.categorizeTransaction(
                event.rawDescription(),
                event.transferDirection(),
                sourceMapping
        );
        UUID categoryAccountId = catResult.getTargetAccountId();
        Account categoryAccount = accountRepository.findById(categoryAccountId).orElse(null);

        BigDecimal walletLegAmount = event.transferDirection() == TransferDirection.IN
                ? exactAmount
                : exactAmount.negate();
        BigDecimal categoryLegAmount = walletLegAmount.negate();

        String entryType = event.transferDirection() == TransferDirection.IN ? "INCOME" : "EXPENSE";
        String normRef = MultiSourceFuzzyDeduplicator.normalizeRefCode(event.extractedReferenceCode());
        String txReference = normRef != null
                ? "INGEST:" + sourceMapping.getProviderCode() + ":" + sourceAccountId.toString().substring(0, 8) + ":" + normRef
                : "INGEST:" + sourceMapping.getProviderCode() + ":" + UUID.randomUUID();

        RecordTransactionRequest txRequest = RecordTransactionRequest.builder()
                .userId(sourceMapping.getUserId())
                .entryType(entryType)
                .walletAccountId(sourceAccountId)
                .categoryAccountId(categoryAccountId)
                .transactionDate(event.occurredAt() != null ? event.occurredAt() : now)
                .description(buildJournalDescription(sourceMapping.getProviderCode(), event))
                .referenceId(txReference)
                .postingLegs(List.of(
                        PostingLegRequest.builder()
                                .accountId(sourceAccountId)
                                .amount(walletLegAmount)
                                .build(),
                        PostingLegRequest.builder()
                                .accountId(categoryAccountId)
                                .amount(categoryLegAmount)
                                .build()
                ))
                .build();

        JournalEntryResponse recorded = ledgerService.recordTransaction(txRequest);
        IngestionFingerprint fp = deduplicator.recordFingerprint(
                sourceAccountId,
                event,
                recorded.getId(),
                event.channel().fingerprintTag()
        );
        recordAuditLog(sourceMapping.getProviderCode(), event, rawMessage, WebhookInboxStatus.PROCESSED, recorded.getId());

        BigDecimal ledgerBal = fetchCurrentLedgerBalance(sourceAccountId);

        return MultiSourceIngestionResult.builder()
                .status("PROCESSED")
                .providerCode(sourceMapping.getProviderCode())
                .channel(event.channel().fingerprintTag())
                .accountIdentifier(sourceMapping.getAccountIdentifier())
                .transferDirection(event.transferDirection().name())
                .amount(exactAmount)
                .runningBalanceAfterTx(sourceMapping.getLastReportedBalance())
                .ledgerBalanceAfterTx(ledgerBal)
                .extractedReferenceCode(event.extractedReferenceCode())
                .counterpartyHint(event.counterpartyHint())
                .cleanDescription(event.rawDescription())
                .sourceAccountId(sourceAccountId)
                .targetCategoryAccountId(categoryAccountId)
                .targetCategoryName(categoryAccount != null ? categoryAccount.getName() : "Phân loại tự động")
                .categorizationSource(catResult.getSource().name())
                .journalEntryId(recorded.getId())
                .sourcesMerged(fp.getSourcesMerged())
                .syncedAt(now)
                .pipelineTrace("DOUBLE_ENTRY_POSTED[" + catResult.getSource().name() + "]")
                .build();
    }

    private BigDecimal fetchCurrentLedgerBalance(UUID accountId) {
        return accountRepository.findById(accountId)
                .map(Account::getCachedBalance)
                .map(b -> b.setScale(4, RoundingMode.HALF_EVEN))
                .orElse(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN));
    }

    private String buildJournalDescription(String providerCode, ParsedBalanceEvent event) {
        String prefix = "[" + providerCode + "] ";
        String body = event.rawDescription() != null && !event.rawDescription().isBlank()
                ? event.rawDescription()
                : "Biến động số dư tự động";
        return prefix + body;
    }

    private void recordAuditLog(
            String providerCode,
            ParsedBalanceEvent event,
            String rawMessage,
            WebhookInboxStatus status,
            UUID journalEntryId) {

        String extId = MultiSourceFuzzyDeduplicator.normalizeRefCode(event.extractedReferenceCode());
        if (extId == null) {
            extId = providerCode + ":" + Instant.now().toEpochMilli();
        } else if (extId.length() > 110) {
            extId = extId.substring(0, 110);
        }

        WebhookInboxLog logEntry = WebhookInboxLog.builder()
                .provider(providerCode)
                .externalTransactionId(extId)
                .sanitizedPayload(piiSanitizer.sanitize(rawMessage))
                .status(status)
                .journalEntryId(journalEntryId)
                .build();
        inboxLogRepository.save(logEntry);
    }
}

