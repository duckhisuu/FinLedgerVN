package vn.finledger.ingestion.parsers;

import java.time.Instant;
import java.util.Optional;

/**
 * Strategy plugin interface for extracting normalized financial events from bank/e-wallet alerts.
 */
public interface FinancialMessageParserPlugin {

    String providerCode();

    boolean supports(String senderHint, String subject, String rawMessage, IngestionChannel channel);

    Optional<ParsedBalanceEvent> parse(
            String senderHint,
            String subject,
            String rawMessage,
            IngestionChannel channel,
            Instant receivedAt
    );
}

