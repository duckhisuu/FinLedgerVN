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
public class DebtSettlementDto {
    private UUID id;
    private UUID fromMemberId;
    private String fromMemberName;
    private UUID toMemberId;
    private String toMemberName;
    private String toBankCode;
    private String toBankAccountNumber;
    private BigDecimal amount;
    private String settlementCode;
    private String status;
    private String vietQrUrl;
}
