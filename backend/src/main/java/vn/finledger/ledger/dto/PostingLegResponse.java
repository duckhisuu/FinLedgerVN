package vn.finledger.ledger.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostingLegResponse {
    private UUID id;
    private UUID accountId;
    private String accountName;
    private BigDecimal amount;
    private Instant createdAt;
}
