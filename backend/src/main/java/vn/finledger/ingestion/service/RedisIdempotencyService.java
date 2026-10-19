package vn.finledger.ingestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisIdempotencyService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "idempotency:webhook:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);
    private static final Duration COMPLETED_TTL = Duration.ofHours(24);

    public String buildKey(String provider, String externalTxId) {
        return KEY_PREFIX + provider.toLowerCase() + ":" + externalTxId;
    }

    /**
     * Attempts to acquire an idempotency lock for the incoming webhook transaction.
     * Uses SET NX EX with 5-minute TTL.
     */
    public IdempotencyLockResult tryAcquireLock(String provider, String externalTxId) {
        String key = buildKey(provider, externalTxId);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "PROCESSING", LOCK_TTL);

        if (Boolean.TRUE.equals(acquired)) {
            log.info("Acquired idempotency lock for key: {}", key);
            return IdempotencyLockResult.builder()
                    .acquired(true)
                    .status(IdempotencyStatus.PROCESSING)
                    .build();
        }

        // Lock not acquired: Check current state in Redis
        String currentValue = redisTemplate.opsForValue().get(key);
        log.warn("Idempotency lock collision for key: {}. Current value: {}", key, currentValue);

        UUID journalEntryId = null;
        IdempotencyStatus status = IdempotencyStatus.PROCESSING;

        if (currentValue != null) {
            if (currentValue.startsWith("COMPLETED")) {
                status = IdempotencyStatus.COMPLETED;
                if (currentValue.contains(":")) {
                    try {
                        String idStr = currentValue.substring(currentValue.indexOf(":") + 1);
                        journalEntryId = UUID.fromString(idStr);
                    } catch (Exception ignored) {
                    }
                }
            } else if ("PROCESSING".equalsIgnoreCase(currentValue)) {
                status = IdempotencyStatus.PROCESSING;
            }
        }

        return IdempotencyLockResult.builder()
                .acquired(false)
                .status(status)
                .existingJournalEntryId(journalEntryId)
                .build();
    }

    /**
     * Marks the webhook transaction as completed in Redis with a 24-hour retention TTL.
     */
    public void markCompleted(String provider, String externalTxId, UUID journalEntryId) {
        String key = buildKey(provider, externalTxId);
        String value = "COMPLETED:" + journalEntryId.toString();
        redisTemplate.opsForValue().set(key, value, COMPLETED_TTL);
        log.info("Marked idempotency key {} as COMPLETED with journalEntryId {}", key, journalEntryId);
    }

    /**
     * Releases lock on failure or rollback, allowing immediate safe retry from the bank webhook.
     */
    public void releaseLockOnFailure(String provider, String externalTxId) {
        String key = buildKey(provider, externalTxId);
        redisTemplate.delete(key);
        log.info("Released idempotency lock for key: {} after failure", key);
    }
}
