package vn.finledger.splitbill.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.finledger.splitbill.domain.GroupExpense;

import java.util.List;
import java.util.UUID;

@Repository
public interface GroupExpenseRepository extends JpaRepository<GroupExpense, UUID> {
    List<GroupExpense> findByGroupIdOrderByCreatedAtDesc(UUID groupId);
}
