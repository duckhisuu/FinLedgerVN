package vn.finledger.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordPersonalTransactionRequest {

    @NotBlank(message = "Loại giao dịch không được để trống (EXPENSE, INCOME, TRANSFER)")
    private String type;

    @NotNull(message = "Số tiền không được để trống")
    @DecimalMin(value = "0.0001", message = "Số tiền phải lớn hơn 0")
    private BigDecimal amount;

    @NotNull(message = "Vui lòng chọn ví")
    private UUID walletId;

    /**
     * Required when type is EXPENSE or INCOME.
     */
    private UUID categoryId;

    /**
     * Required when type is TRANSFER.
     */
    private UUID toWalletId;

    private String description;

    private Instant transactionDate;
}

