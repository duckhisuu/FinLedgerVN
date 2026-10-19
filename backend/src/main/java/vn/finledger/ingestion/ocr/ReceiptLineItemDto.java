package vn.finledger.ingestion.ocr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Line item extracted from a supermarket receipt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptLineItemDto {
    private String productName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineAmount;
}
