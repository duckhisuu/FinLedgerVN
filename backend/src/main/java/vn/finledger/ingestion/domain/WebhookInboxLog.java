package vn.finledger.ingestion.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_inbox_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookInboxLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 64)
    private String provider;

    @Column(name = "external_transaction_id", nullable = false, length = 128)
    private String externalTransactionId;

    @Column(name = "sanitized_payload", columnDefinition = "TEXT")
    private String sanitizedPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WebhookInboxStatus status;

    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "received_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) receivedAt = Instant.now();
    }
}
