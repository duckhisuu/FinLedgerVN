package vn.finledger.ledger.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordTransactionRequest {

    private UUID userId;

    private String entryType;

    private UUID walletAccountId;

    private UUID categoryAccountId;

    private Instant transactionDate;

    @NotBlank(message = "Transaction description is mandatory")
    private String description;

    private String referenceId;

    @NotEmpty(message = "Transaction must contain at least two posting legs")
    @Valid
    private List<PostingLegRequest> postingLegs;
}
