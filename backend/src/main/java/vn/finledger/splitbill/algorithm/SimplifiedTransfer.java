package vn.finledger.splitbill.algorithm;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A simplified, transitively settled transfer edge between two members.
 */
public record SimplifiedTransfer(
        UUID fromMemberId,
        UUID toMemberId,
        BigDecimal amount,
        String explanation
) {}
