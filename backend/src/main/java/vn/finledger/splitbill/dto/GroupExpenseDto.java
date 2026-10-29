package vn.finledger.splitbill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupExpenseDto {
    private UUID id;
    private String title;
    private BigDecimal totalAmount;
    private String splitStrategy;
    private UUID paidByMemberId;
    private String paidByMemberName;
    private Instant createdAt;
    private List<GroupExpenseShareDto> shares;
}
