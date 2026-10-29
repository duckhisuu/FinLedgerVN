package vn.finledger.finance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.finledger.finance.domain.Budget;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByUserIdAndMonthYearOrderByCreatedAtAsc(UUID userId, String monthYear);

    Optional<Budget> findByUserIdAndCategoryAccountIdAndMonthYear(UUID userId, UUID categoryAccountId, String monthYear);

    Optional<Budget> findByUserIdAndCategoryAccountIdIsNullAndMonthYear(UUID userId, String monthYear);

    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);
}

