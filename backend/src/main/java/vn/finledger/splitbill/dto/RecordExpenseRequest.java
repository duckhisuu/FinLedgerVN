package vn.finledger.splitbill.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.finledger.splitbill.calculator.SplitShareInput;
import vn.finledger.splitbill.domain.SplitStrategy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordExpenseRequest {
    @NotNull
    private UUID paidByMemberId;
    @NotBlank
    private String title;
    @NotNull
    @DecimalMin(value = "0.01", message = "Total amount must be positive")
    private BigDecimal totalAmount;
    @NotNull
    private SplitStrategy splitStrategy;
    private List<SplitShareInput> splitInputs;
}
