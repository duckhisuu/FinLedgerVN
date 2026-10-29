package vn.finledger.splitbill.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when a debt settlement is finalized.
 */
public record DebtSettledEvent(
        UUID groupId,
        String shareToken,
        UUID settlementId,
        String settlementCode,
        UUID fromMemberId,
        UUID toMemberId,
        BigDecimal amount,
        Instant settledAt
) {}
