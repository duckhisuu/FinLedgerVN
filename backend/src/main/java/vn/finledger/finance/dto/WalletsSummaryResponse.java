package vn.finledger.finance.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletsSummaryResponse {
    private BigDecimal totalBalance;
    private BigDecimal totalAssets;
    private BigDecimal totalCreditDebt;
    private List<WalletDto> wallets;
}

