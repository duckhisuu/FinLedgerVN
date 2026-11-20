package vn.finledger.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
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
public class ConnectFinancialSourceRequest {

    @NotBlank(message = "Provider code (VCB hoặc MOMO) là bắt buộc")
    private String providerCode;

    @NotBlank(message = "Số tài khoản VCB hoặc SĐT ví MoMo là bắt buộc")
    private String accountIdentifier;

    private UUID walletId;

    private String walletName;

    private BigDecimal initialBalance;
}

