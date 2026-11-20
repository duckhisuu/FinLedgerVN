package vn.finledger.ingestion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.finledger.ingestion.domain.IngestionFingerprint;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngestionFingerprintRepository extends JpaRepository<IngestionFingerprint, UUID> {

    Optional<IngestionFingerprint> findByFuzzySignature(String fuzzySignature);

    Optional<IngestionFingerprint> findFirstBySourceAccountIdAndNormalizedRefCodeOrderByCreatedAtDesc(
            UUID sourceAccountId, String normalizedRefCode);

    @Query("""
            SELECT f FROM IngestionFingerprint f
            WHERE f.sourceAccountId = :sourceAccountId
              AND f.transferDirection = :transferDirection
              AND f.exactAmount = :exactAmount
              AND f.timeBucket5m IN :buckets
            ORDER BY f.createdAt DESC
            """)
    List<IngestionFingerprint> findRecentFuzzyMatches(
            @Param("sourceAccountId") UUID sourceAccountId,
            @Param("transferDirection") String transferDirection,
            @Param("exactAmount") BigDecimal exactAmount,
            @Param("buckets") Collection<Long> buckets);

    List<IngestionFingerprint> findByJournalEntryId(UUID journalEntryId);

    List<IngestionFingerprint> findTop25BySourceAccountIdInOrderByCreatedAtDesc(Collection<UUID> sourceAccountIds);
}

