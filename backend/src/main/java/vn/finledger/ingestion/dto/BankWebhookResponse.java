package vn.finledger.ingestion.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankWebhookResponse {
    private String status; // "PROCESSED", "DUPLICATE_IGNORED", "FAILED"
    private UUID journalEntryId;
    private String message;
}
