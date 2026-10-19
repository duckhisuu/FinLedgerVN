package vn.finledger.ledger.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordTransactionRequest {

    private Instant transactionDate;

    @NotBlank(message = "Transaction description is mandatory")
    private String description;

    private String referenceId;

    @NotEmpty(message = "Transaction must contain at least two posting legs")
    @Valid
    private List<PostingLegRequest> postingLegs;
}
