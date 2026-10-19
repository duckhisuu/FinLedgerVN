package vn.finledger.ledger.dto;

import lombok.*;
import vn.finledger.ledger.domain.AccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponse {
    private UUID id;
    private String name;
    private AccountType accountType;
    private String currency;
    private BigDecimal cachedBalance;
    private boolean allowNegative;
    private Instant createdAt;
    private Instant updatedAt;
}
