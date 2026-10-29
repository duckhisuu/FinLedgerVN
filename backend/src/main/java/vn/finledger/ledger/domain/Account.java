package vn.finledger.ledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32)
    private AccountType accountType;

    @Column(name = "account_subtype", length = 32)
    private String accountSubtype;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "cached_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal cachedBalance = BigDecimal.ZERO.setScale(4);

    @Column(name = "allow_negative", nullable = false)
    @Builder.Default
    private boolean allowNegative = false;

    @Column(length = 64)
    private String icon;

    @Column(length = 32)
    private String color;

    @Column(name = "bank_code", length = 32)
    private String bankCode;

    @Column(name = "account_number", length = 64)
    private String accountNumber;

    @Column(name = "include_in_total", nullable = false)
    @Builder.Default
    private boolean includeInTotal = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean archived = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (cachedBalance == null) {
            cachedBalance = BigDecimal.ZERO.setScale(4);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
