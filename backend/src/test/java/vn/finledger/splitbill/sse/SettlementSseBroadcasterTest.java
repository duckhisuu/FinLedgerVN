package vn.finledger.splitbill.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.finledger.splitbill.event.DebtSettledEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class SettlementSseBroadcasterTest {

    private final SettlementSseBroadcaster broadcaster = new SettlementSseBroadcaster();

    @Test
    @DisplayName("Subscribe registers emitter and broadcast sends event without throwing")
    void testSubscribeAndBroadcast() {
        String token = "test-token-123";

        SseEmitter emitter = broadcaster.subscribe(token);
        assertThat(emitter).isNotNull();
        assertThat(broadcaster.getSubscriberCount(token)).isEqualTo(1);

        DebtSettledEvent event = new DebtSettledEvent(
                UUID.randomUUID(),
                token,
                UUID.randomUUID(),
                "FLV888999",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("500000.0000"),
                Instant.now()
        );

        // Broadcast to subscriber
        broadcaster.broadcast(token, event);

        // Broadcast to non-existent token should be safely handled
        broadcaster.broadcast("unknown-token", event);
    }
}
