package vn.finledger.ingestion.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "bank_account_mappings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccountMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "bank_account_number", nullable = false, unique = true, length = 64)
    private String bankAccountNumber;

    @Column(name = "bank_name", nullable = false, length = 64)
    private String bankName;

    @Column(name = "provider_code", nullable = false, length = 32)
    private String providerCode; // VCB, MOMO, SEPAY

    @Column(name = "account_identifier", nullable = false, length = 64)
    private String accountIdentifier;

    @Column(name = "webhook_secret_token", nullable = false, length = 128)
    private String webhookSecretToken;

    @Column(name = "last_reported_balance", precision = 19, scale = 4)
    private BigDecimal lastReportedBalance;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "ledger_account_id", nullable = false)
    private UUID ledgerAccountId;

    @Column(name = "default_suspense_expense_account_id", nullable = false)
    private UUID defaultSuspenseExpenseAccountId;

    @Column(name = "default_suspense_revenue_account_id", nullable = false)
    private UUID defaultSuspenseRevenueAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        ensureDefaults();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        ensureDefaults();
    }

    private void ensureDefaults() {
        if (providerCode == null || providerCode.isBlank()) {
            providerCode = inferProviderCode(bankName);
        } else {
            providerCode = providerCode.trim().toUpperCase(Locale.ROOT);
        }
        if (accountIdentifier == null || accountIdentifier.isBlank()) {
            accountIdentifier = bankAccountNumber != null ? bankAccountNumber.trim() : "";
        } else {
            accountIdentifier = accountIdentifier.trim();
        }
        if (webhookSecretToken == null || webhookSecretToken.isBlank()) {
            webhookSecretToken = "flb_" + UUID.randomUUID().toString().replace("-", "");
        }
    }

    public static String inferProviderCode(String bankNameOrCode) {
        if (bankNameOrCode == null || bankNameOrCode.isBlank()) {
            return "SEPAY";
        }
        String upper = bankNameOrCode.trim().toUpperCase(Locale.ROOT);
        if (upper.contains("VCB") || upper.contains("VIETCOMBANK")) {
            return "VCB";
        }
        if (upper.contains("MOMO")) {
            return "MOMO";
        }
        return "SEPAY";
    }
}
