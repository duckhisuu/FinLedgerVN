package vn.finledger.ledger.exception;

import java.util.UUID;

public class AccountNotFoundException extends LedgerException {
    public AccountNotFoundException(UUID accountId) {
        super("Account not found: " + accountId);
    }

    public AccountNotFoundException(String message) {
        super(message);
    }
}
