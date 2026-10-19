package vn.finledger.ingestion.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyLockResult {
    private boolean acquired;
    private IdempotencyStatus status;
    private UUID existingJournalEntryId;
}
