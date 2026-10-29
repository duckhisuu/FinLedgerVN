package vn.finledger.splitbill.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.finledger.splitbill.domain.ExpenseGroup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseGroupRepository extends JpaRepository<ExpenseGroup, UUID> {
    Optional<ExpenseGroup> findByShareToken(String shareToken);

    List<ExpenseGroup> findByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);

    Optional<ExpenseGroup> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}
