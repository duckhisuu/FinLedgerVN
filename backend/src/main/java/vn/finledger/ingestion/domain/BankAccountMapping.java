package vn.finledger.ingestion.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
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

    @Column(name = "bank_account_number", nullable = false, unique = true, length = 64)
    private String bankAccountNumber;

    @Column(name = "bank_name", nullable = false, length = 64)
    private String bankName;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
