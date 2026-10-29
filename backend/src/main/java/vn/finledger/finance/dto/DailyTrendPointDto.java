package vn.finledger.finance.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyTrendPointDto {
    private int day;
    private BigDecimal dailySpent;
    private BigDecimal dailyIncome;
    private BigDecimal cumulativeSpent;
    private BigDecimal cumulativeIncome;
    private BigDecimal prev3MonthAvgCumulativeSpent;
    private BigDecimal prev3MonthAvgCumulativeIncome;
}

