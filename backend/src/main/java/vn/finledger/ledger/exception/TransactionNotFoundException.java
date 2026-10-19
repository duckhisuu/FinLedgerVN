package vn.finledger.ledger.exception;

import java.util.UUID;

public class TransactionNotFoundException extends LedgerException {
    public TransactionNotFoundException(UUID transactionId) {
        super("Journal entry transaction not found: " + transactionId);
    }

    public TransactionNotFoundException(String message) {
        super(message);
    }
}
