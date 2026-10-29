package vn.finledger.finance.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalTransactionDto {
    private UUID id;
    private String type; // EXPENSE, INCOME, TRANSFER, OPENING_BALANCE, ADJUSTMENT, RECEIPT_SCAN
    private BigDecimal amount;
    private String description;
    private Instant transactionDate;
    private Instant createdAt;

    private UUID walletId;
    private String walletName;
    private String walletIcon;
    private String walletColor;

    private UUID categoryId;
    private String categoryName;
    private String categoryIcon;
    private String categoryColor;

    private UUID toWalletId;
    private String toWalletName;
    private String toWalletIcon;
    private String toWalletColor;

    private boolean cancelled;
    private UUID reversalOfId;

    private List<SplitItemBreakdown> breakdown;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SplitItemBreakdown {
        private UUID categoryId;
        private String categoryName;
        private String categoryColor;
        private BigDecimal amount;
    }
}

