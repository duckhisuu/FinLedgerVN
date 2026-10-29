package vn.finledger.finance.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletDto {
    private UUID id;
    private String name;
    private String subtype;
    private String currency;
    private BigDecimal balance;
    private String icon;
    private String color;
    private String bankCode;
    private String accountNumber;
    private boolean includeInTotal;
    private boolean allowNegative;
    private Instant createdAt;
}

