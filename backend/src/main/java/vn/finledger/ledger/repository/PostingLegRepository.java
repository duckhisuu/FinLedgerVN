package vn.finledger.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.finledger.ledger.domain.PostingLeg;

import java.util.List;
import java.util.UUID;

@Repository
public interface PostingLegRepository extends JpaRepository<PostingLeg, UUID> {

    List<PostingLeg> findByJournalEntryId(UUID journalEntryId);

    List<PostingLeg> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
}
