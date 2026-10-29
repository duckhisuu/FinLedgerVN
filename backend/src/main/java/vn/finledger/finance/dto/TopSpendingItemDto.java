package vn.finledger.finance.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopSpendingItemDto {
    private UUID categoryId;
    private String categoryName;
    private String icon;
    private String color;
    private BigDecimal amount;
    private BigDecimal percentage;
}

