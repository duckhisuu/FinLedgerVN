package vn.finledger.ingestion.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categorization_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategorizationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "keyword_pattern", nullable = false)
    private String keywordPattern;

    @Column(name = "transfer_type", nullable = false, length = 10)
    private String transferType; // "IN" or "OUT"

    @Column(name = "target_account_id", nullable = false)
    private UUID targetAccountId;

    @Column(nullable = false)
    @Builder.Default
    private int priority = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
