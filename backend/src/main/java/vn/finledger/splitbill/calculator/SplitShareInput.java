package vn.finledger.splitbill.calculator;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Input for expense allocation per member (amount, percentage, or shares).
 */
public record SplitShareInput(
        UUID memberId,
        BigDecimal amountOrWeight
) {
    public SplitShareInput(UUID memberId) {
        this(memberId, null);
    }
}
