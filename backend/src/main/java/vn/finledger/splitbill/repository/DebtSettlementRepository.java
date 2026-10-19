package vn.finledger.splitbill.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.finledger.splitbill.domain.DebtSettlement;
import vn.finledger.splitbill.domain.SettlementStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DebtSettlementRepository extends JpaRepository<DebtSettlement, UUID> {
    List<DebtSettlement> findByGroupIdAndStatus(UUID groupId, SettlementStatus status);
    Optional<DebtSettlement> findBySettlementCodeAndStatus(String settlementCode, SettlementStatus status);
    Optional<DebtSettlement> findBySettlementCode(String settlementCode);
}
