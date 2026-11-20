package vn.finledger.ingestion.dedup;

/**
 * Thrown when mobile-bridge or inbound-email authentication token is missing or invalid.
 */
public class UnauthorizedBridgeException extends RuntimeException {
    public UnauthorizedBridgeException(String message) {
        super(message);
    }
}

