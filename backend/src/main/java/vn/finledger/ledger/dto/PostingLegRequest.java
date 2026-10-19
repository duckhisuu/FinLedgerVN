package vn.finledger.ledger.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostingLegRequest {

    @NotNull(message = "Account ID is mandatory")
    private UUID accountId;

    @NotNull(message = "Amount is mandatory")
    private BigDecimal amount;
}
