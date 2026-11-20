package vn.finledger.ingestion.dedup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import vn.finledger.ingestion.domain.BankAccountMapping;
import vn.finledger.ingestion.parsers.FinancialMessageParserPlugin;
import vn.finledger.ingestion.parsers.IngestionChannel;
import vn.finledger.ingestion.parsers.ParsedBalanceEvent;
import vn.finledger.ingestion.repository.BankAccountMappingRepository;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Coordinates parser plugin resolution, constant-time bridge/webhook authentication,
 * Redis distributed locking (eliminating Push vs Email or VCB vs MoMo race conditions),
 * and delegation to MultiSourceTransactionalProcessor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiSourceIngestionService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    private final List<FinancialMessageParserPlugin> parserPlugins;
    private final BankAccountMappingRepository mappingRepository;
    private final MultiSourceTransactionalProcessor transactionalProcessor;
    private final StringRedisTemplate redisTemplate;

    @Value("${finledger.webhook.api-key:finledger_secret_webhook_key_2026}")
    private String configuredWebhookApiKey;

    public MultiSourceIngestionResult ingestMobileBridgeAlert(
            String bridgeToken,
            String senderHint,
            String rawMessage,
            String accountIdentifierOverride,
            Instant receivedAt) {

        if (bridgeToken == null || bridgeToken.isBlank()) {
            throw new UnauthorizedBridgeException("Thiếu header X-Bridge-Token xác thực.");
        }

        List<BankAccountMapping> matchedByToken = mappingRepository.findByWebhookSecretToken(bridgeToken.trim());
        if (matchedByToken.isEmpty() || !anyTokenEqualsConstantTime(matchedByToken, bridgeToken.trim())) {
            throw new UnauthorizedBridgeException("X-Bridge-Token không hợp lệ.");
        }

        ParsedBalanceEvent parsed = parseWithPlugins(
                senderHint,
                null,
                rawMessage,
                IngestionChannel.MOBILE_NOTIFICATION,
                receivedAt
        );

        BankAccountMapping targetMapping = resolveTargetMappingForUserMappings(
                matchedByToken,
                parsed,
                accountIdentifierOverride
        );

        return executeLockedIngestion(targetMapping, parsed, rawMessage);
    }

    public MultiSourceIngestionResult ingestInboundEmail(
            String bridgeToken,
            String emailApiKeyHeader,
            String senderHint,
            String subject,
            String rawBody,
            String accountIdentifierOverride,
            Instant receivedAt) {

        List<BankAccountMapping> candidateMappings = Collections.emptyList();
        boolean globalKeyAuthorized = false;

        if (bridgeToken != null && !bridgeToken.isBlank()) {
            candidateMappings = mappingRepository.findByWebhookSecretToken(bridgeToken.trim());
            if (candidateMappings.isEmpty() || !anyTokenEqualsConstantTime(candidateMappings, bridgeToken.trim())) {
                throw new UnauthorizedBridgeException("X-Bridge-Token không hợp lệ cho inbound-email.");
            }
        } else if (isValidGlobalEmailSecret(emailApiKeyHeader)) {
            globalKeyAuthorized = true;
        } else {
            throw new UnauthorizedBridgeException("Yêu cầu X-Bridge-Token hoặc API Key hợp lệ cho inbound-email.");
        }

        ParsedBalanceEvent parsed = parseWithPlugins(
                senderHint,
                subject,
                rawBody,
                IngestionChannel.EMAIL,
                receivedAt
        );

        BankAccountMapping targetMapping;
        if (!candidateMappings.isEmpty()) {
            targetMapping = resolveTargetMappingForUserMappings(candidateMappings, parsed, accountIdentifierOverride);
        } else if (globalKeyAuthorized) {
            targetMapping = resolveMappingGlobally(parsed, accountIdentifierOverride);
        } else {
            throw new UnauthorizedBridgeException("Không xác thực được nguồn email đến.");
        }

        return executeLockedIngestion(targetMapping, parsed, rawBody);
    }

    private MultiSourceIngestionResult executeLockedIngestion(
            BankAccountMapping sourceMapping,
            ParsedBalanceEvent parsed,
            String rawMessage) {

        String userKey = sourceMapping.getUserId() != null ? sourceMapping.getUserId().toString() : "GLOBAL";
        String amountStr = parsed.amount().setScale(4, RoundingMode.HALF_EVEN).toPlainString();
        String lockKey = parsed.linkedBankTransfer()
                ? "ingestion:lock:intx:" + userKey + ":" + amountStr
                : "ingestion:lock:acct:" + sourceMapping.getLedgerAccountId() + ":" + amountStr;
        String lockVal = UUID.randomUUID().toString();

        boolean acquired = acquireSpinLock(lockKey, lockVal);
        try {
            return transactionalProcessor.processInTransaction(sourceMapping, parsed, rawMessage);
        } finally {
            if (acquired) {
                releaseSpinLock(lockKey, lockVal);
            }
        }
    }

    private ParsedBalanceEvent parseWithPlugins(
            String senderHint,
            String subject,
            String rawMessage,
            IngestionChannel channel,
            Instant receivedAt) {

        if (rawMessage == null || rawMessage.isBlank()) {
            throw new IllegalArgumentException("Nội dung thông báo biến động số dư không được để trống.");
        }

        for (FinancialMessageParserPlugin plugin : parserPlugins) {
            if (plugin.supports(senderHint, subject, rawMessage, channel)) {
                Optional<ParsedBalanceEvent> parsedOpt = plugin.parse(senderHint, subject, rawMessage, channel, receivedAt);
                if (parsedOpt.isPresent()) {
                    return parsedOpt.get();
                }
            }
        }

        throw new IllegalArgumentException("Không nhận diện được định dạng thông báo Vietcombank hoặc MoMo.");
    }

    private BankAccountMapping resolveTargetMappingForUserMappings(
            List<BankAccountMapping> tokenMappings,
            ParsedBalanceEvent parsed,
            String accountIdentifierOverride) {

        UUID ownerUserId = tokenMappings.stream()
                .map(BankAccountMapping::getUserId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        String identifier = firstNonBlank(parsed.accountIdentifier(), accountIdentifierOverride);
        String provider = parsed.providerCode().toUpperCase(Locale.ROOT);

        if (ownerUserId != null) {
            if (identifier != null) {
                Optional<BankAccountMapping> exact = mappingRepository
                        .findByUserIdAndProviderCodeAndAccountIdentifier(ownerUserId, provider, identifier);
                if (exact.isPresent()) {
                    return exact.get();
                }
            }
            Optional<BankAccountMapping> byProvider = mappingRepository
                    .findFirstByUserIdAndProviderCodeOrderByCreatedAtAsc(ownerUserId, provider);
            if (byProvider.isPresent()) {
                return byProvider.get();
            }
        }

        // Legacy fallback when user_id is null on test/standalone mappings
        if (identifier != null) {
            Optional<BankAccountMapping> tokenExact = tokenMappings.stream()
                    .filter(m -> provider.equalsIgnoreCase(m.getProviderCode())
                            && identifier.equalsIgnoreCase(m.getAccountIdentifier()))
                    .findFirst();
            if (tokenExact.isPresent()) {
                return tokenExact.get();
            }
        }
        Optional<BankAccountMapping> tokenByProvider = tokenMappings.stream()
                .filter(m -> provider.equalsIgnoreCase(m.getProviderCode()))
                .findFirst();
        if (tokenByProvider.isPresent()) {
            return tokenByProvider.get();
        }

        throw new IllegalArgumentException(
                "Chưa cấu hình liên kết nguồn tiền cho " + provider
                        + (identifier != null ? " (" + identifier + ")" : "") + ".");
    }

    private BankAccountMapping resolveMappingGlobally(ParsedBalanceEvent parsed, String accountIdentifierOverride) {
        String identifier = firstNonBlank(parsed.accountIdentifier(), accountIdentifierOverride);
        String provider = parsed.providerCode().toUpperCase(Locale.ROOT);

        if (identifier != null) {
            Optional<BankAccountMapping> exact = mappingRepository
                    .findByProviderCodeAndAccountIdentifier(provider, identifier);
            if (exact.isPresent()) {
                return exact.get();
            }
            Optional<BankAccountMapping> byNumber = mappingRepository.findByBankAccountNumber(identifier);
            if (byNumber.isPresent()) {
                return byNumber.get();
            }
        }
        throw new IllegalArgumentException(
                "Không tìm thấy nguồn tiền " + provider + " phù hợp với email nhận vào.");
    }

    private boolean anyTokenEqualsConstantTime(List<BankAccountMapping> mappings, String providedToken) {
        byte[] providedBytes = providedToken.getBytes(StandardCharsets.UTF_8);
        boolean matched = false;
        for (BankAccountMapping m : mappings) {
            if (m.getWebhookSecretToken() != null) {
                byte[] storedBytes = m.getWebhookSecretToken().getBytes(StandardCharsets.UTF_8);
                if (MessageDigest.isEqual(storedBytes, providedBytes)) {
                    matched = true;
                }
            }
        }
        return matched;
    }

    private boolean isValidGlobalEmailSecret(String providedSecret) {
        if (providedSecret == null || providedSecret.isBlank()) {
            return false;
        }
        String clean = providedSecret.startsWith("Bearer ")
                ? providedSecret.substring(7).trim()
                : providedSecret.trim();
        return MessageDigest.isEqual(
                configuredWebhookApiKey.getBytes(StandardCharsets.UTF_8),
                clean.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean acquireSpinLock(String lockKey, String lockVal) {
        long deadline = System.currentTimeMillis() + 3000L;
        while (System.currentTimeMillis() < deadline) {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(lockKey, lockVal, LOCK_TTL);
            if (Boolean.TRUE.equals(ok)) {
                return true;
            }
            try {
                Thread.sleep(25L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private void releaseSpinLock(String lockKey, String lockVal) {
        try {
            String current = redisTemplate.opsForValue().get(lockKey);
            if (lockVal.equals(current)) {
                redisTemplate.delete(lockKey);
            }
        } catch (Exception ignored) {
            // TTL expires safely
        }
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return null;
    }
}

