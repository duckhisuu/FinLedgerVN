package vn.finledger.ledger.exception;

public class InsufficientFundsException extends LedgerException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
