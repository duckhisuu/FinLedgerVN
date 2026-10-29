package vn.finledger.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpsertBudgetRequest {

    /**
     * Null means Overall Monthly Spending Limit.
     */
    private UUID categoryId;

    @NotBlank(message = "Tháng áp dụng không được để trống (YYYY-MM)")
    private String monthYear;

    @NotNull(message = "Hạn mức ngân sách không được để trống")
    @DecimalMin(value = "1", message = "Hạn mức ngân sách phải lớn hơn 0")
    private BigDecimal amountLimit;

    private String note;
}

