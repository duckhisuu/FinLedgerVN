package vn.finledger.splitbill.algorithm;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A direct directed debt edge from a debtor to a creditor.
 */
public record RawDebtEdge(
        UUID debtorId,
        UUID creditorId,
        BigDecimal amount
) {}
