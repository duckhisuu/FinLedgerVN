package vn.finledger.ledger.exception;

public class UnbalancedTransactionException extends LedgerException {
    public UnbalancedTransactionException(String message) {
        super(message);
    }
}
