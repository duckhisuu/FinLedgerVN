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
public class ConnectedSourceTelemetryDto {
    private UUID id;
    private String providerCode;
    private String bankName;
    private String accountIdentifier;
    private UUID walletId;
    private String walletName;
    private BigDecimal ledgerBalance;
    private BigDecimal lastReportedBalance;
    private BigDecimal reconciliationDelta;
    private boolean reconciled;
    private Instant lastSyncedAt;
    private String webhookSecretToken;
}

