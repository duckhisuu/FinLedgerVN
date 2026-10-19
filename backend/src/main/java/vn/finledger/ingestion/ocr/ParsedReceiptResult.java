package vn.finledger.ingestion.ocr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of structured receipt extraction including parsed items and detected totals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedReceiptResult {
    @Builder.Default
    private List<ReceiptLineItemDto> lineItems = new ArrayList<>();
    private BigDecimal detectedTotalAmount;
    private BigDecimal calculatedSumAmount;
    private String merchantName;
}
