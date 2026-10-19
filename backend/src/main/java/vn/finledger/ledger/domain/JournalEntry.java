package vn.finledger.ledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "journal_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_date", nullable = false)
    private Instant transactionDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "reference_id", length = 128, unique = true)
    private String referenceId;

    @Column(name = "reversal_of_id")
    private UUID reversalOfId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PostingLeg> postingLegs = new ArrayList<>();

    public void addPostingLeg(PostingLeg leg) {
        postingLegs.add(leg);
        leg.setJournalEntry(this);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (transactionDate == null) {
            transactionDate = Instant.now();
        }
    }
}
