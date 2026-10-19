package vn.finledger.ingestion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.finledger.ingestion.domain.BankAccountMapping;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankAccountMappingRepository extends JpaRepository<BankAccountMapping, UUID> {

    Optional<BankAccountMapping> findByBankAccountNumber(String bankAccountNumber);
}
