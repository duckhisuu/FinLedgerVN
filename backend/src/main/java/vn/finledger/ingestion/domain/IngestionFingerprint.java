package vn.finledger.ingestion.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_fingerprints")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestionFingerprint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "transfer_direction", nullable = false, length = 8)
    private String transferDirection; // "IN" or "OUT"

    @Column(name = "exact_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal exactAmount;

    @Column(name = "time_bucket_5m", nullable = false)
    private Long timeBucket5m;

    @Column(name = "normalized_ref_code", length = 64)
    private String normalizedRefCode;

    @Column(name = "fuzzy_signature", nullable = false, unique = true, length = 128)
    private String fuzzySignature;

    @Column(name = "journal_entry_id", nullable = false)
    private UUID journalEntryId;

    @Column(name = "sources_merged", nullable = false, length = 128)
    private String sourcesMerged;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

