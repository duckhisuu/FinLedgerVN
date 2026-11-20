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
public class MobileBridgeAlertRequest {

    @JsonAlias({"message", "text", "content", "notificationText"})
    private String rawMessage;

    @JsonAlias({"sender", "appPackage", "provider", "sourceApp"})
    private String senderHint;

    @JsonAlias({"accountNumber", "phone"})
    private String accountIdentifier;

    private Instant receivedAt;
}

