package vn.finledger.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncedEventLogDto {
    private UUID id;
    private String providerCode;
    private String walletName;
    private String transferDirection;
    private String entryType;
    private BigDecimal amount;
    private String description;
    private String extractedReferenceCode;
    private String sourcesMerged;
    private UUID journalEntryId;
    private Instant createdAt;
}

