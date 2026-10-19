package vn.finledger.ingestion.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorizationResult {
    private UUID targetAccountId;
    private BigDecimal confidenceScore;
    private CategorizationSource source;
    private String matchedRuleOrReason;
}
