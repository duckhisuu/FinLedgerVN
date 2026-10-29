package vn.finledger.finance.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomeOverviewResponse {
    private BigDecimal totalBalance;
    private String currentMonth;
    private List<WalletDto> wallets;

    // Report this month
    private BigDecimal monthSpent;
    private BigDecimal monthIncome;
    private BigDecimal prev3MonthAvgSpent;
    private BigDecimal prev3MonthAvgIncome;
    private List<DailyTrendPointDto> dailyTrend;

    // Top spending (WEEK & MONTH precomputed so toggle is instant!)
    private List<TopSpendingItemDto> topSpendingMonth;
    private List<TopSpendingItemDto> topSpendingWeek;

    // Recent transactions
    private List<PersonalTransactionDto> recentTransactions;

    // Budget notifications
    private List<BudgetAlertDto> budgetAlerts;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BudgetAlertDto {
        private UUID budgetId;
        private String categoryName;
        private BigDecimal amountLimit;
        private BigDecimal spentAmount;
        private BigDecimal percentage;
        private boolean exceeded;
    }
}

