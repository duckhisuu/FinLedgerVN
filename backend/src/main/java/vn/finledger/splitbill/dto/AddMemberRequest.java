package vn.finledger.splitbill.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddMemberRequest {
    @NotBlank
    private String displayName;
    @Builder.Default
    private boolean isGuest = true;
    private String bankCode;
    private String bankAccountNumber;
}
