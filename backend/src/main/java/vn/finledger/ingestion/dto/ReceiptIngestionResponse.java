package vn.finledger.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.finledger.ingestion.ocr.ReceiptLineItemDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Response returned after a supermarket receipt is successfully processed and recorded.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptIngestionResponse {
    private UUID journalEntryId;
    private String status;
    private String merchantName;
    private BigDecimal totalPaidAmount;
    private List<ReceiptLineItemDto> items;
    private int legCount;
    private String message;
}
