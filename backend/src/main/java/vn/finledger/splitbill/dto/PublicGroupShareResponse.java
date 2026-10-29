package vn.finledger.splitbill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicGroupShareResponse {
    private UUID groupId;
    private String groupName;
    private String currency;
    private String shareToken;
    private boolean simplifyDebts;
    private Instant createdAt;
    private List<GroupMemberDto> members;
    private List<GroupExpenseDto> expenses;
    private Map<UUID, BigDecimal> netBalances;
    private List<DebtSettlementDto> settlements;
}
