package vn.finledger.ledger.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.finledger.ledger.domain.Account;
import vn.finledger.ledger.domain.AccountType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id IN :ids ORDER BY a.id ASC")
    List<Account> findAllByIdInOrderByIdAscForUpdate(@Param("ids") Collection<UUID> ids);

    List<Account> findByUserIdAndArchivedFalseOrderByCreatedAtAsc(UUID userId);

    List<Account> findByUserIdOrderByCreatedAtAsc(UUID userId);

    List<Account> findByUserIdAndAccountTypeAndArchivedFalseOrderByCreatedAtAsc(UUID userId, AccountType accountType);

    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    Optional<Account> findFirstByUserIdAndAccountSubtype(UUID userId, String accountSubtype);
}
