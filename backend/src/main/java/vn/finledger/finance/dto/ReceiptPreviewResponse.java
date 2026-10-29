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
public class ReceiptPreviewResponse {
    private String merchantName;
    private BigDecimal detectedTotalAmount;
    private BigDecimal computedSum;
    private List<ReceiptPreviewItem> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReceiptPreviewItem {
        private String productName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineAmount;
        private UUID suggestedCategoryId;
        private String suggestedCategoryName;
    }
}

