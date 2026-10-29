package vn.finledger.ledger.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.finledger.ledger.domain.JournalEntry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByReferenceId(String referenceId);

    boolean existsByReferenceId(String referenceId);

    boolean existsByReversalOfId(UUID reversalOfId);

    List<JournalEntry> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"postingLegs", "postingLegs.account"})
    List<JournalEntry> findByUserIdOrderByTransactionDateDescCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"postingLegs", "postingLegs.account"})
    @Query("SELECT j FROM JournalEntry j WHERE j.userId = :userId AND j.transactionDate >= :start AND j.transactionDate < :end ORDER BY j.transactionDate DESC, j.createdAt DESC")
    List<JournalEntry> findByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("SELECT j.reversalOfId FROM JournalEntry j WHERE j.userId = :userId AND j.reversalOfId IS NOT NULL")
    Set<UUID> findReversedEntryIdsByUserId(@Param("userId") UUID userId);

    Optional<JournalEntry> findByIdAndUserId(UUID id, UUID userId);
}
