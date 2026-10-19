package vn.finledger.ledger.exception;

public class DuplicateTransactionException extends LedgerException {
    public DuplicateTransactionException(String referenceId) {
        super("Transaction with referenceId already exists: " + referenceId);
    }
}
