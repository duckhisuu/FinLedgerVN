package vn.finledger.ledger.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntryResponse {
    private UUID id;
    private Instant transactionDate;
    private String description;
    private String referenceId;
    private UUID reversalOfId;
    private Instant createdAt;
    private List<PostingLegResponse> postingLegs;
}
