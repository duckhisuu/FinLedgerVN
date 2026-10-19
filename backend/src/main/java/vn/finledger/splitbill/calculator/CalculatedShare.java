package vn.finledger.splitbill.calculator;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Result of penny-exact allocation for a single member.
 */
public record CalculatedShare(
        UUID memberId,
        BigDecimal shareAmount,
        BigDecimal rawWeight
) {}
