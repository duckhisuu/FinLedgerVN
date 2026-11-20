package vn.finledger.ingestion.dedup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.finledger.ingestion.domain.BankAccountMapping;
import vn.finledger.ingestion.domain.IngestionFingerprint;
import vn.finledger.ingestion.parsers.ParsedBalanceEvent;
import vn.finledger.ingestion.parsers.TransferDirection;
import vn.finledger.ingestion.repository.BankAccountMappingRepository;
import vn.finledger.ledger.dto.JournalEntryResponse;
import vn.finledger.ledger.dto.PostingLegRequest;
import vn.finledger.ledger.dto.RecordTransactionRequest;
import vn.finledger.ledger.service.LedgerService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Automatically detects and reconciles internal transfers between connected VCB and MoMo sources.
 * Records a single balanced ASSET-to-ASSET TRANSFER JournalEntry (EXPENSE = 0, REVENUE = 0),
 * storing a pending_internal_transfer key in Redis (TTL 10m) so the counterpart alert links
 * directly into the same JournalEntry in either arrival order (VCB -> MoMo or MoMo -> VCB).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalTransferReconciler {

    private static final Duration PENDING_TRANSFER_TTL = Duration.ofMinutes(10);

    private final BankAccountMappingRepository mappingRepository;
    private final MultiSourceFuzzyDeduplicator deduplicator;
    private final LedgerService ledgerService;
    private final StringRedisTemplate redisTemplate;

    public record InternalReconciliationResult(
            boolean handled,
            String status, // INTERNAL_TRANSFER_CREATED | INTERNAL_TRANSFER_LINKED
            UUID journalEntryId,
            UUID sourceAccountId,
            UUID counterpartAccountId,
            String counterpartProvider,
            String sourcesMerged
    ) {
        public static InternalReconciliationResult notInternal() {
            return new InternalReconciliationResult(false, null, null, null, null, null, null);
        }
    }

    /**
     * Attempts to handle the event as an internal transfer between VCB and MoMo.
     */
    @Transactional
    public InternalReconciliationResult reconcileIfInternalTransfer(
            BankAccountMapping sourceMapping,
            ParsedBalanceEvent event) {

        if (!isInternalTransferCandidate(sourceMapping, event)) {
            return InternalReconciliationResult.notInternal();
        }

        String counterpartProvider = event.counterpartyProviderCode().toUpperCase();
        Optional<BankAccountMapping> counterpartOpt = findCounterpartMapping(sourceMapping.getUserId(), counterpartProvider);
        if (counterpartOpt.isEmpty()) {
            log.debug("Counterpart {} mapping not found for user {} - falling back to regular categorization",
                    counterpartProvider, sourceMapping.getUserId());
            return InternalReconciliationResult.notInternal();
        }

        BankAccountMapping counterpartMapping = counterpartOpt.get();
        if (counterpartMapping.getLedgerAccountId().equals(sourceMapping.getLedgerAccountId())) {
            return InternalReconciliationResult.notInternal();
        }

        BigDecimal exactAmount = event.amount().setScale(4, RoundingMode.HALF_EVEN);
        String userKey = sourceMapping.getUserId() != null ? sourceMapping.getUserId().toString() : "GLOBAL";

        // 1. Check if the counterpart side ALREADY created the JournalEntry and left a pending link key in Redis
        String expectedKey = buildPendingKey(
                userKey,
                sourceMapping.getProviderCode(),
                event.transferDirection().name(),
                exactAmount
        );
        String pendingValue = redisTemplate.opsForValue().get(expectedKey);
        if (pendingValue != null && !pendingValue.isBlank()) {
            try {
                UUID existingJournalEntryId = UUID.fromString(pendingValue.trim());
                redisTemplate.delete(expectedKey);

                IngestionFingerprint linkedFp = deduplicator.recordFingerprint(
                        sourceMapping.getLedgerAccountId(),
                        event,
                        existingJournalEntryId,
                        event.channel().fingerprintTag()
                );

                log.info("Linked counterpart internal transfer {} ({} -> {}) into existing JournalEntry {}",
                        sourceMapping.getProviderCode(), exactAmount, counterpartProvider, existingJournalEntryId);

                return new InternalReconciliationResult(
                        true,
                        "INTERNAL_TRANSFER_LINKED",
                        existingJournalEntryId,
                        sourceMapping.getLedgerAccountId(),
                        counterpartMapping.getLedgerAccountId(),
                        counterpartProvider,
                        linkedFp.getSourcesMerged()
                );
            } catch (IllegalArgumentException e) {
                log.warn("Invalid pending_internal_transfer Redis value {}: {}", pendingValue, e.getMessage());
            }
        }

        // 2. First side arriving: determine FROM (credit/negative) and TO (debit/positive) wallets
        UUID fromAccountId;
        UUID toAccountId;
        String fromProvider;
        String toProvider;
        if (event.transferDirection() == TransferDirection.OUT) {
            fromAccountId = sourceMapping.getLedgerAccountId();
            fromProvider = sourceMapping.getProviderCode();
            toAccountId = counterpartMapping.getLedgerAccountId();
            toProvider = counterpartProvider;
        } else {
            fromAccountId = counterpartMapping.getLedgerAccountId();
            fromProvider = counterpartProvider;
            toAccountId = sourceMapping.getLedgerAccountId();
            toProvider = sourceMapping.getProviderCode();
        }

        String refCode = MultiSourceFuzzyDeduplicator.normalizeRefCode(event.extractedReferenceCode());
        String txRef = refCode != null
                ? "INTX:" + fromProvider + ":" + toProvider + ":" + refCode
                : "INTX:" + fromProvider + ":" + toProvider + ":" + UUID.randomUUID();

        String desc = String.format("Chuyển tiền nội bộ (%s -> %s): %s",
                fromProvider, toProvider,
                event.rawDescription() != null ? event.rawDescription() : "Nạp/Rút liên kết");

        RecordTransactionRequest txRequest = RecordTransactionRequest.builder()
                .userId(sourceMapping.getUserId())
                .entryType("TRANSFER")
                .walletAccountId(fromAccountId)
                .categoryAccountId(toAccountId)
                .transactionDate(event.occurredAt() != null ? event.occurredAt() : Instant.now())
                .description(desc)
                .referenceId(txRef)
                .postingLegs(List.of(
                        PostingLegRequest.builder()
                                .accountId(fromAccountId)
                                .amount(exactAmount.negate())
                                .build(),
                        PostingLegRequest.builder()
                                .accountId(toAccountId)
                                .amount(exactAmount)
                                .build()
                ))
                .build();

        JournalEntryResponse recorded = ledgerService.recordTransaction(txRequest);

        IngestionFingerprint fp = deduplicator.recordFingerprint(
                sourceMapping.getLedgerAccountId(),
                event,
                recorded.getId(),
                event.channel().fingerprintTag()
        );

        // 3. Register pending_internal_transfer key on Redis (TTL 10 minutes) for the opposite side
        TransferDirection expectedOppositeDir = event.transferDirection() == TransferDirection.OUT
                ? TransferDirection.IN
                : TransferDirection.OUT;
        String counterpartWaitKey = buildPendingKey(
                userKey,
                counterpartProvider,
                expectedOppositeDir.name(),
                exactAmount
        );
        redisTemplate.opsForValue().set(counterpartWaitKey, recorded.getId().toString(), PENDING_TRANSFER_TTL);

        log.info("Created internal transfer JournalEntry {} ({} -> {}, {} VND), set Redis key {}",
                recorded.getId(), fromProvider, toProvider, exactAmount, counterpartWaitKey);

        return new InternalReconciliationResult(
                true,
                "INTERNAL_TRANSFER_CREATED",
                recorded.getId(),
                sourceMapping.getLedgerAccountId(),
                counterpartMapping.getLedgerAccountId(),
                counterpartProvider,
                fp.getSourcesMerged()
        );
    }

    private boolean isInternalTransferCandidate(BankAccountMapping sourceMapping, ParsedBalanceEvent event) {
        if (!event.linkedBankTransfer() || event.counterpartyProviderCode() == null) {
            return false;
        }
        String srcProvider = sourceMapping.getProviderCode() != null
                ? sourceMapping.getProviderCode().toUpperCase()
                : "";
        String dstProvider = event.counterpartyProviderCode().toUpperCase();
        return (srcProvider.equals("VCB") && dstProvider.equals("MOMO"))
                || (srcProvider.equals("MOMO") && dstProvider.equals("VCB"));
    }

    private Optional<BankAccountMapping> findCounterpartMapping(UUID userId, String providerCode) {
        if (userId != null) {
            return mappingRepository.findFirstByUserIdAndProviderCodeOrderByCreatedAtAsc(userId, providerCode);
        }
        return mappingRepository.findAll().stream()
                .filter(m -> providerCode.equalsIgnoreCase(m.getProviderCode()))
                .findFirst();
    }

    public static String buildPendingKey(
            String userKey,
            String expectedProvider,
            String expectedDirection,
            BigDecimal exactAmount) {

        return "pending_internal_transfer:" + userKey + ":"
                + expectedProvider.toUpperCase() + ":"
                + expectedDirection.toUpperCase() + ":"
                + exactAmount.setScale(4, RoundingMode.HALF_EVEN).toPlainString();
    }
}
