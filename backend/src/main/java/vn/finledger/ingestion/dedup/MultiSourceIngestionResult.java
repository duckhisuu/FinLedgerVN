package vn.finledger.ingestion.dedup;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class MultiSourceIngestionResult {
    private String status; // PROCESSED | MERGED_DUPLICATE | INTERNAL_TRANSFER_CREATED | INTERNAL_TRANSFER_LINKED
    private String providerCode;
    private String channel;
    private String accountIdentifier;
    private String transferDirection;
    private BigDecimal amount;
    private BigDecimal runningBalanceAfterTx;
    private BigDecimal ledgerBalanceAfterTx;
    private String extractedReferenceCode;
    private String counterpartyHint;
    private String cleanDescription;
    private UUID sourceAccountId;
    private UUID journalEntryId;
    private UUID targetCategoryAccountId;
    private String targetCategoryName;
    private String categorizationSource;
    private String sourcesMerged;
    private Instant syncedAt;
    private String pipelineTrace;
}

