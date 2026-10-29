package vn.finledger.finance.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetsOverviewResponse {
    private String monthYear;
    private int daysInMonth;
    private int daysRemaining;
    private BigDecimal totalBudgetLimit;
    private BigDecimal totalSpentThisMonth;
    private BigDecimal totalRemaining;
    private BigDecimal recommendedDailySpend;
    private List<BudgetDto> budgets;
}

