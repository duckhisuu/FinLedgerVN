package vn.finledger.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BankWebhookPayload {

    private Object id;

    private String gateway;

    @JsonAlias({"transactionDate", "transaction_date"})
    private String transactionDate;

    @JsonAlias({"accountNumber", "account_number"})
    private String accountNumber;

    private String content;

    @JsonAlias({"transferType", "transfer_type"})
    private String transferType; // "in" or "out"

    @JsonAlias({"transferAmount", "transfer_amount", "amount"})
    private BigDecimal transferAmount;

    @JsonAlias({"referenceCode", "reference_code", "reference"})
    private String referenceCode;

    public String getExternalId() {
        if (id != null) {
            return String.valueOf(id);
        }
        if (referenceCode != null && !referenceCode.isBlank()) {
            return referenceCode;
        }
        return "UNKNOWN-" + System.currentTimeMillis();
    }
}
