package vn.finledger.splitbill.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.finledger.splitbill.domain.GroupExpenseShare;

import java.util.List;
import java.util.UUID;

@Repository
public interface GroupExpenseShareRepository extends JpaRepository<GroupExpenseShare, UUID> {
    List<GroupExpenseShare> findByGroupExpenseId(UUID groupExpenseId);
}
