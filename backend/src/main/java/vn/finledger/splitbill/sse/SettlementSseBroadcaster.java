package vn.finledger.splitbill.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe SSE Broadcaster managing client connections per group share token.
 * Automatically cleans up dead emitters to prevent memory leaks.
 */
@Slf4j
@Component
public class SettlementSseBroadcaster {

    // 30 minutes connection timeout
    private static final long SSE_TIMEOUT_MS = 30L * 60 * 1000L;

    private final Map<String, List<SseEmitter>> emittersByToken = new ConcurrentHashMap<>();

    /**
     * Subscribes a client to SSE events for a specific group share token.
     */
    public SseEmitter subscribe(String shareToken) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emittersByToken.computeIfAbsent(shareToken, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(shareToken, emitter, "completed"));
        emitter.onTimeout(() -> removeEmitter(shareToken, emitter, "timed out"));
        emitter.onError(e -> removeEmitter(shareToken, emitter, "error: " + e.getMessage()));

        // Send initialization event immediately
        try {
            emitter.send(SseEmitter.event()
                    .name("INIT_CONNECTED")
                    .data(Map.of(
                            "status", "CONNECTED",
                            "shareToken", shareToken,
                            "timestamp", System.currentTimeMillis()
                    ), MediaType.APPLICATION_JSON));
            log.info("Client subscribed to SSE for shareToken={}", shareToken);
        } catch (IOException e) {
            log.warn("Failed to send initial SSE event to client for shareToken={}", shareToken);
            removeEmitter(shareToken, emitter, "failed initial event");
        }

        return emitter;
    }

    /**
     * Broadcasts an event to all connected clients for a share token.
     */
    public void broadcast(String shareToken, Object data) {
        List<SseEmitter> emitters = emittersByToken.get(shareToken);
        if (emitters == null || emitters.isEmpty()) {
            log.debug("No active SSE subscribers for shareToken={}", shareToken);
            return;
        }

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("DEBT_SETTLED")
                        .data(data, MediaType.APPLICATION_JSON));
            } catch (Exception ex) {
                log.warn("Error sending SSE to emitter for shareToken={}: {}", shareToken, ex.getMessage());
                deadEmitters.add(emitter);
            }
        }

        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
            if (emitters.isEmpty()) {
                emittersByToken.remove(shareToken);
            }
        }
        log.info("Broadcasted DEBT_SETTLED to {} subscribers for shareToken={}",
                emitters.size() - deadEmitters.size(), shareToken);
    }

    private void removeEmitter(String shareToken, SseEmitter emitter, String reason) {
        log.debug("Removing SSE emitter for shareToken={} (reason: {})", shareToken, reason);
        List<SseEmitter> list = emittersByToken.get(shareToken);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emittersByToken.remove(shareToken);
            }
        }
    }

    /**
     * Helper for test assertions.
     */
    public int getSubscriberCount(String shareToken) {
        List<SseEmitter> list = emittersByToken.get(shareToken);
        return list != null ? list.size() : 0;
    }
}
