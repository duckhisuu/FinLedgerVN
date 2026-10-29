package vn.finledger.finance.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialReportsResponse {
    private String selectedMonth;
    private BigDecimal netWorth;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netSavings;
    private BigDecimal savingsRatePercent;

    private List<MonthlyCashflowItem> monthlyHistory;
    private List<TopSpendingItemDto> expenseByCategory;
    private List<TopSpendingItemDto> incomeByCategory;
    private List<WalletDto> wallets;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyCashflowItem {
        private String month;
        private String label;
        private BigDecimal income;
        private BigDecimal expense;
        private BigDecimal net;
    }
}

