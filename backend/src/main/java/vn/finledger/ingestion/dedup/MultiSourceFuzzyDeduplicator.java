package vn.finledger.ingestion.dedup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.finledger.ingestion.domain.IngestionFingerprint;
import vn.finledger.ingestion.parsers.IngestionChannel;
import vn.finledger.ingestion.parsers.ParsedBalanceEvent;
import vn.finledger.ingestion.parsers.TransferDirection;
import vn.finledger.ingestion.repository.IngestionFingerprintRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Multi-source fuzzy deduplicator using Redis sliding window + PostgreSQL ingestion_fingerprints.
 * Prevents duplicate JournalEntry creation when App Push, SMS, and Email arrive for the same event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiSourceFuzzyDeduplicator {

    private static final long BUCKET_SECONDS = 300L; // 5-minute window
    private static final Duration REDIS_TTL = Duration.ofMinutes(15);

    private final IngestionFingerprintRepository fingerprintRepository;
    private final StringRedisTemplate redisTemplate;

    public record DedupCheckResult(
            boolean duplicate,
            UUID existingFingerprintId,
            UUID journalEntryId,
            String sourcesMerged
    ) {
        public static DedupCheckResult notDuplicate() {
            return new DedupCheckResult(false, null, null, null);
        }
    }

    /**
     * Checks if the normalized event matches an already ingested transaction via either:
     * 1) Exact reference code match on Redis/DB
     * 2) 5-minute sliding window fuzzy match on (sourceAccountId, direction, exactAmount, timeBucket5m +/- 1)
     * When matched, merges the new source channel into sources_merged without creating a new JournalEntry.
     */
    @Transactional
    public DedupCheckResult checkAndMergeIfDuplicate(UUID sourceAccountId, ParsedBalanceEvent event) {
        BigDecimal exactAmount = event.amount().setScale(4, RoundingMode.HALF_EVEN);
        String direction = event.transferDirection().name();
        Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        long currentBucket = Math.floorDiv(occurredAt.getEpochSecond(), BUCKET_SECONDS);
        String normalizedRef = normalizeRefCode(event.extractedReferenceCode());
        String channelTag = event.channel().fingerprintTag();

        // 1. Exact Reference Code Check (Redis first -> DB fallback)
        if (normalizedRef != null) {
            String refKey = buildRefRedisKey(sourceAccountId, normalizedRef);
            String cachedFpId = redisTemplate.opsForValue().get(refKey);
            Optional<IngestionFingerprint> exactMatch = Optional.empty();
            if (cachedFpId != null && !cachedFpId.isBlank()) {
                try {
                    exactMatch = fingerprintRepository.findById(UUID.fromString(cachedFpId));
                } catch (IllegalArgumentException ignored) {
                    // Fallback to DB lookup
                }
            }
            if (exactMatch.isEmpty()) {
                exactMatch = fingerprintRepository
                        .findFirstBySourceAccountIdAndNormalizedRefCodeOrderByCreatedAtDesc(sourceAccountId, normalizedRef);
            }
            if (exactMatch.isPresent()) {
                IngestionFingerprint merged = mergeChannelIntoFingerprint(exactMatch.get(), channelTag, normalizedRef);
                cacheFingerprintInRedis(merged, occurredAt);
                log.info("Exact ref dedup HIT for account {} ref={} -> merged sources={}",
                        sourceAccountId, normalizedRef, merged.getSourcesMerged());
                return new DedupCheckResult(true, merged.getId(), merged.getJournalEntryId(), merged.getSourcesMerged());
            }
        }

        // 2. Fuzzy 5-minute Sliding Window Check across current and adjacent buckets
        List<Long> candidateBuckets = List.of(currentBucket - 1, currentBucket, currentBucket + 1);

        // Fast Redis check across adjacent buckets
        for (Long bucket : candidateBuckets) {
            String fuzzyKey = buildFuzzyRedisKey(sourceAccountId, direction, exactAmount, bucket);
            String cachedVal = redisTemplate.opsForValue().get(fuzzyKey);
            if (cachedVal != null) {
                Optional<IngestionFingerprint> fpOpt = parseRedisCandidateIfCompatible(cachedVal, normalizedRef, occurredAt);
                if (fpOpt.isPresent()) {
                    IngestionFingerprint merged = mergeChannelIntoFingerprint(fpOpt.get(), channelTag, normalizedRef);
                    cacheFingerprintInRedis(merged, occurredAt);
                    log.info("Redis fuzzy dedup HIT for account {} amount={} bucket={} -> merged sources={}",
                            sourceAccountId, exactAmount, bucket, merged.getSourcesMerged());
                    return new DedupCheckResult(true, merged.getId(), merged.getJournalEntryId(), merged.getSourcesMerged());
                }
            }
        }

        // Authoritative PostgreSQL check in case Redis cache expired or restarted
        List<IngestionFingerprint> dbCandidates = fingerprintRepository.findRecentFuzzyMatches(
                sourceAccountId, direction, exactAmount, candidateBuckets);

        for (IngestionFingerprint candidate : dbCandidates) {
            if (areCompatibleInFuzzyWindow(candidate, normalizedRef, occurredAt, currentBucket)) {
                IngestionFingerprint merged = mergeChannelIntoFingerprint(candidate, channelTag, normalizedRef);
                cacheFingerprintInRedis(merged, occurredAt);
                log.info("DB fuzzy dedup HIT for account {} amount={} -> merged sources={}",
                        sourceAccountId, exactAmount, merged.getSourcesMerged());
                return new DedupCheckResult(true, merged.getId(), merged.getJournalEntryId(), merged.getSourcesMerged());
            }
        }

        return DedupCheckResult.notDuplicate();
    }

    /**
     * Records a new ingestion fingerprint in both PostgreSQL and Redis after a JournalEntry is persisted/linked.
     */
    @Transactional
    public IngestionFingerprint recordFingerprint(
            UUID sourceAccountId,
            ParsedBalanceEvent event,
            UUID journalEntryId,
            String initialSourceTagOverride) {

        BigDecimal exactAmount = event.amount().setScale(4, RoundingMode.HALF_EVEN);
        String direction = event.transferDirection().name();
        Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        long bucket5m = Math.floorDiv(occurredAt.getEpochSecond(), BUCKET_SECONDS);
        String normalizedRef = normalizeRefCode(event.extractedReferenceCode());
        String initialSources = (initialSourceTagOverride != null && !initialSourceTagOverride.isBlank())
                ? initialSourceTagOverride
                : event.channel().fingerprintTag();

        String fuzzySignature = computeFuzzySignature(
                sourceAccountId, direction, exactAmount, bucket5m, normalizedRef);

        // Guard against race if exact same signature already exists
        Optional<IngestionFingerprint> existing = fingerprintRepository.findByFuzzySignature(fuzzySignature);
        if (existing.isPresent()) {
            IngestionFingerprint merged = mergeChannelIntoFingerprint(existing.get(), initialSources, normalizedRef);
            cacheFingerprintInRedis(merged, occurredAt);
            return merged;
        }

        IngestionFingerprint fp = IngestionFingerprint.builder()
                .sourceAccountId(sourceAccountId)
                .transferDirection(direction)
                .exactAmount(exactAmount)
                .timeBucket5m(bucket5m)
                .normalizedRefCode(normalizedRef)
                .fuzzySignature(fuzzySignature)
                .journalEntryId(journalEntryId)
                .sourcesMerged(initialSources)
                .build();

        IngestionFingerprint saved = fingerprintRepository.save(fp);
        cacheFingerprintInRedis(saved, occurredAt);
        return saved;
    }

    private IngestionFingerprint mergeChannelIntoFingerprint(
            IngestionFingerprint existing,
            String incomingSourcesCsv,
            String incomingRefCode) {

        Set<String> mergedSet = new LinkedHashSet<>();
        for (String token : existing.getSourcesMerged().split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) mergedSet.add(trimmed);
        }
        if (incomingSourcesCsv != null) {
            for (String token : incomingSourcesCsv.split(",")) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) mergedSet.add(trimmed);
            }
        }
        String joined = String.join(",", mergedSet);
        if (joined.length() > 128) {
            joined = joined.substring(0, 128);
        }
        existing.setSourcesMerged(joined);

        if ((existing.getNormalizedRefCode() == null || existing.getNormalizedRefCode().isBlank())
                && incomingRefCode != null) {
            existing.setNormalizedRefCode(incomingRefCode);
        }

        return fingerprintRepository.save(existing);
    }

    private Optional<IngestionFingerprint> parseRedisCandidateIfCompatible(
            String cachedVal, String incomingRef, Instant incomingTime) {
        // Format: fingerprintId|occurredEpochSec
        String[] parts = cachedVal.split("\\|");
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            UUID fpId = UUID.fromString(parts[0]);
            long cachedEpochSec = Long.parseLong(parts[1]);
            if (Math.abs(incomingTime.getEpochSecond() - cachedEpochSec) > BUCKET_SECONDS) {
                return Optional.empty();
            }
            Optional<IngestionFingerprint> fpOpt = fingerprintRepository.findById(fpId);
            if (fpOpt.isEmpty()) {
                return Optional.empty();
            }
            IngestionFingerprint fp = fpOpt.get();
            if (hasConflictingRefCodes(fp.getNormalizedRefCode(), incomingRef)) {
                return Optional.empty();
            }
            return Optional.of(fp);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private boolean areCompatibleInFuzzyWindow(
            IngestionFingerprint candidate,
            String incomingRef,
            Instant incomingTime,
            long currentBucket) {

        if (hasConflictingRefCodes(candidate.getNormalizedRefCode(), incomingRef)) {
            return false;
        }
        // Within +/- 1 5-minute bucket (or timestamp diff <= 300s from fingerprint creation if recent)
        long bucketDiff = Math.abs(candidate.getTimeBucket5m() - currentBucket);
        return bucketDiff <= 1;
    }

    private boolean hasConflictingRefCodes(String refA, String refB) {
        if (refA == null || refA.isBlank() || refB == null || refB.isBlank()) {
            return false;
        }
        return !refA.equalsIgnoreCase(refB);
    }

    private void cacheFingerprintInRedis(IngestionFingerprint fp, Instant occurredAt) {
        long epochSec = occurredAt != null ? occurredAt.getEpochSecond() : Instant.now().getEpochSecond();
        if (fp.getNormalizedRefCode() != null && !fp.getNormalizedRefCode().isBlank()) {
            redisTemplate.opsForValue().set(
                    buildRefRedisKey(fp.getSourceAccountId(), fp.getNormalizedRefCode()),
                    fp.getId().toString(),
                    REDIS_TTL
            );
        }
        String fuzzyKey = buildFuzzyRedisKey(
                fp.getSourceAccountId(),
                fp.getTransferDirection(),
                fp.getExactAmount(),
                fp.getTimeBucket5m()
        );
        redisTemplate.opsForValue().set(
                fuzzyKey,
                fp.getId() + "|" + epochSec,
                REDIS_TTL
        );
    }

    public static String normalizeRefCode(String rawRef) {
        if (rawRef == null || rawRef.isBlank()) {
            return null;
        }
        String cleaned = rawRef.trim().toUpperCase(Locale.ROOT);
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }

    public static String computeFuzzySignature(
            UUID sourceAccountId,
            String direction,
            BigDecimal exactAmount,
            long bucket5m,
            String normalizedRefCode) {

        String seed = sourceAccountId + "|" + direction + "|"
                + exactAmount.setScale(4, RoundingMode.HALF_EVEN).toPlainString() + "|"
                + bucket5m + "|"
                + (normalizedRefCode != null ? normalizedRefCode : "NOREF");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(seed.hashCode()) + "_" + bucket5m;
        }
    }

    private String buildRefRedisKey(UUID sourceAccountId, String normalizedRef) {
        return "ingestion:dedup:ref:" + sourceAccountId + ":" + normalizedRef;
    }

    private String buildFuzzyRedisKey(UUID sourceAccountId, String direction, BigDecimal exactAmount, long bucket5m) {
        return "ingestion:dedup:fuzzy:" + sourceAccountId + ":" + direction + ":"
                + exactAmount.setScale(4, RoundingMode.HALF_EVEN).toPlainString() + ":" + bucket5m;
    }
}

