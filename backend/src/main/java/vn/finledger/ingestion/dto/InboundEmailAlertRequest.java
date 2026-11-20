package vn.finledger.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundEmailAlertRequest {

    private String subject;

    @JsonAlias({"text", "rawMessage", "htmlBody", "content"})
    private String body;

    @JsonAlias({"from", "senderHint"})
    private String sender;

    @JsonAlias({"accountNumber", "phone"})
    private String accountIdentifier;

    private Instant receivedAt;
}

