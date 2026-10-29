package vn.finledger.finance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWalletRequest {

    @NotBlank(message = "Tên ví không được để trống")
    private String name;

    @Builder.Default
    private String subtype = "CASH";

    @Builder.Default
    private BigDecimal initialBalance = BigDecimal.ZERO;

    private String icon;
    private String color;
    private String bankCode;
    private String accountNumber;

    @Builder.Default
    private boolean includeInTotal = true;

    private Boolean allowNegative;
}

