package vn.finledger.splitbill.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.finledger.splitbill.event.DebtSettledEvent;
import vn.finledger.splitbill.sse.SettlementSseBroadcaster;

/**
 * Event listener that catches DebtSettledEvent strictly AFTER transaction commits,
 * broadcasting real-time updates to all SSE subscribers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebtSettledEventListener {

    private final SettlementSseBroadcaster sseBroadcaster;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDebtSettled(DebtSettledEvent event) {
        log.info("Transaction committed. Broadcasting DebtSettledEvent for code={} to shareToken={}",
                event.settlementCode(), event.shareToken());
        sseBroadcaster.broadcast(event.shareToken(), event);
    }
}
