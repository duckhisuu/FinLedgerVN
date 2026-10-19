package vn.finledger.ingestion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.finledger.ingestion.domain.CategorizationRule;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategorizationRuleRepository extends JpaRepository<CategorizationRule, UUID> {

    @Query("SELECT r FROM CategorizationRule r WHERE r.active = true AND r.transferType = :transferType ORDER BY r.priority DESC, r.createdAt ASC")
    List<CategorizationRule> findActiveRulesByTransferType(@Param("transferType") String transferType);
}
