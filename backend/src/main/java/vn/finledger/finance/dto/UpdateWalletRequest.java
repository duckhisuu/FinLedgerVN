package vn.finledger.finance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateWalletRequest {

    @NotBlank(message = "Tên ví không được để trống")
    private String name;

    private String icon;
    private String color;
    private String bankCode;
    private String accountNumber;
    private Boolean includeInTotal;
    private Boolean allowNegative;

    /**
     * Optional target balance: if provided and differs from current cachedBalance,
     * an immutable Double-Entry ADJUSTMENT transaction is automatically recorded.
     */
    private BigDecimal targetBalance;
}

