package vn.finledger.ingestion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.finledger.ingestion.domain.WebhookInboxLog;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookInboxLogRepository extends JpaRepository<WebhookInboxLog, UUID> {

    Optional<WebhookInboxLog> findByProviderAndExternalTransactionId(String provider, String externalTransactionId);
}
