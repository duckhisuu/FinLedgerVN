package vn.finledger.splitbill.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "group_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ExpenseGroup group;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "is_guest", nullable = false)
    @Builder.Default
    private boolean isGuest = true;

    @Column(name = "bank_code", length = 32)
    private String bankCode;

    @Column(name = "bank_account_number", length = 64)
    private String bankAccountNumber;

    @Column(name = "member_ledger_account_id", nullable = false)
    private UUID memberLedgerAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
