package vn.finledger.ingestion.service;

public enum IdempotencyStatus {
    PROCESSING,
    COMPLETED,
    FAILED,
    NOT_FOUND
}
