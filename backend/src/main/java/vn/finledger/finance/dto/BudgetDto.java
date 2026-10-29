package vn.finledger.finance.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetDto {
    private UUID id;
    private UUID categoryId; // null = Total monthly budget
    private String categoryName;
    private String categoryIcon;
    private String categoryColor;
    private String monthYear;
    private BigDecimal amountLimit;
    private BigDecimal spentAmount;
    private BigDecimal remainingAmount;
    private BigDecimal percentage;
    private boolean overBudget;
    private String note;
}

