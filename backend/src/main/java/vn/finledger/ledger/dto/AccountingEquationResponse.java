package vn.finledger.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingEquationResponse {
    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal totalEquity;
    private BigDecimal totalRevenue;
    private BigDecimal totalExpense;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private BigDecimal sumDifference;
    private boolean balanced;
    private String statusMessage;
}
