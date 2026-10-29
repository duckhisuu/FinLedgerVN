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
public class GroupMemberDto {
    private UUID id;
    private String displayName;
    private boolean isGuest;
    private String bankCode;
    private String bankAccountNumber;
    private UUID memberLedgerAccountId;
    private BigDecimal netBalance;
}
