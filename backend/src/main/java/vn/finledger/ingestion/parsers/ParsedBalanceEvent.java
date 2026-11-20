package vn.finledger.ingestion.parsers;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Canonical normalized balance event produced by a FinancialMessageParserPlugin.
 */
public record ParsedBalanceEvent(
        String providerCode,
        IngestionChannel channel,
        String accountIdentifier,
        TransferDirection transferDirection,
        BigDecimal amount,
        BigDecimal runningBalanceAfterTx,
        String rawDescription,
        String extractedReferenceCode,
        String counterpartyHint,
        boolean linkedBankTransfer,
        String counterpartyProviderCode,
        Instant occurredAt
) {
    public ParsedBalanceEvent(
            String providerCode,
            IngestionChannel channel,
            String accountIdentifier,
            TransferDirection transferDirection,
            BigDecimal amount,
            BigDecimal runningBalanceAfterTx,
            String rawDescription,
            String extractedReferenceCode,
            String counterpartyHint,
            Instant occurredAt
    ) {
        this(
                providerCode,
                channel,
                accountIdentifier,
                transferDirection,
                amount,
                runningBalanceAfterTx,
                rawDescription,
                extractedReferenceCode,
                counterpartyHint,
                counterpartyHint != null && ("VCB".equalsIgnoreCase(counterpartyHint) || "MOMO".equalsIgnoreCase(counterpartyHint)),
                counterpartyHint != null && ("VCB".equalsIgnoreCase(counterpartyHint) || "MOMO".equalsIgnoreCase(counterpartyHint))
                        ? counterpartyHint.toUpperCase()
                        : null,
                occurredAt
        );
    }
}

