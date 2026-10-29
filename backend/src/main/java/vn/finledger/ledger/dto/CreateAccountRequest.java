package vn.finledger.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import vn.finledger.ledger.domain.AccountType;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAccountRequest {

    private UUID userId;

    @NotBlank(message = "Account name is mandatory")
    private String name;

    @NotNull(message = "Account type is mandatory")
    private AccountType accountType;

    private String accountSubtype;

    @Builder.Default
    private String currency = "VND";

    @Builder.Default
    private BigDecimal initialBalance = BigDecimal.ZERO;

    @Builder.Default
    private boolean allowNegative = false;

    private String icon;

    private String color;

    private String bankCode;

    private String accountNumber;

    @Builder.Default
    private boolean includeInTotal = true;
}
