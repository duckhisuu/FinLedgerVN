package vn.finledger.splitbill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupExpenseShareDto {
    private UUID memberId;
    private String memberName;
    private BigDecimal shareAmount;
    private BigDecimal rawWeight;
}
