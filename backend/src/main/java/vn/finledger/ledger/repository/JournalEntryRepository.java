package vn.finledger.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.finledger.ledger.domain.JournalEntry;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByReferenceId(String referenceId);

    boolean existsByReferenceId(String referenceId);

    boolean existsByReversalOfId(UUID reversalOfId);
}
