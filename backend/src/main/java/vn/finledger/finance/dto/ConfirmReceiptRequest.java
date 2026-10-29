package vn.finledger.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class ConfirmReceiptRequest {

    @NotNull(message = "Vui lòng chọn ví thanh toán")
    private UUID walletId;

    private String merchantName;

    private Instant transactionDate;

    private BigDecimal actualPaidAmount;

    @NotEmpty(message = "Hóa đơn cần ít nhất 1 mặt hàng")
    @Valid
    private List<ConfirmReceiptItem> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConfirmReceiptItem {
        @NotBlank(message = "Tên mặt hàng không được để trống")
        private String productName;

        @NotNull(message = "Số tiền mặt hàng không được để trống")
        @DecimalMin(value = "0.0001", message = "Số tiền phải lớn hơn 0")
        private BigDecimal lineAmount;

        @NotNull(message = "Vui lòng chọn danh mục chi tiêu")
        private UUID categoryId;
    }
}

