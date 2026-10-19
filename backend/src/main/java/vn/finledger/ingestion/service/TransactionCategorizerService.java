package vn.finledger.ingestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.finledger.ingestion.domain.BankAccountMapping;
import vn.finledger.ingestion.domain.CategorizationRule;
import vn.finledger.ingestion.repository.CategorizationRuleRepository;
import vn.finledger.ingestion.security.PiiSanitizer;
import vn.finledger.ledger.domain.Account;
import vn.finledger.ledger.repository.AccountRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionCategorizerService {

    private final PiiSanitizer piiSanitizer;
    private final CategorizationRuleRepository categorizationRuleRepository;
    private final LlmCategorizerClient llmCategorizerClient;
    private final AccountRepository accountRepository;

    private static final BigDecimal AI_CONFIDENCE_THRESHOLD = new BigDecimal("0.70");

    @Transactional(readOnly = true)
    public CategorizationResult categorize(
            String rawDescription,
            String transferType,
            BankAccountMapping mapping) {

        String normType = transferType != null ? transferType.trim().toUpperCase() : "OUT";

        // Step 1: PII Sanitization
        String sanitized = piiSanitizer.sanitize(rawDescription);
        log.debug("Sanitized transaction description: '{}' -> '{}'", rawDescription, sanitized);

        // Step 2: Fast Path - Rule Engine (priority DESC)
        List<CategorizationRule> rules = categorizationRuleRepository.findActiveRulesByTransferType(normType);
        for (CategorizationRule rule : rules) {
            try {
                Pattern pattern = Pattern.compile(rule.getKeywordPattern(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                if (pattern.matcher(sanitized).find()) {
                    log.info("Matched rule pattern '{}' with priority {} for account {}",
                            rule.getKeywordPattern(), rule.getPriority(), rule.getTargetAccountId());
                    return CategorizationResult.builder()
                            .targetAccountId(rule.getTargetAccountId())
                            .confidenceScore(BigDecimal.ONE.setScale(4))
                            .source(CategorizationSource.RULE_BASED)
                            .matchedRuleOrReason(rule.getKeywordPattern())
                            .build();
                }
            } catch (Exception e) {
                log.warn("Invalid regex pattern in categorization rule ID {}: {}", rule.getId(), rule.getKeywordPattern());
            }
        }

        // Step 3: Slow Path - AI Fallback
        List<Account> candidateAccounts = accountRepository.findAll();
        Optional<CategorizationResult> aiResult = llmCategorizerClient.categorizeWithAi(sanitized, normType, candidateAccounts);
        if (aiResult.isPresent()) {
            CategorizationResult result = aiResult.get();
            if (result.getConfidenceScore() != null && result.getConfidenceScore().compareTo(AI_CONFIDENCE_THRESHOLD) >= 0) {
                log.info("AI categorizer matched account {} with confidence {}",
                        result.getTargetAccountId(), result.getConfidenceScore());
                return result;
            } else {
                log.info("AI categorizer confidence {} is below threshold {}",
                        result.getConfidenceScore(), AI_CONFIDENCE_THRESHOLD);
            }
        }

        // Step 4: Suspense Fallback
        UUID suspenseAccountId = "OUT".equalsIgnoreCase(normType)
                ? mapping.getDefaultSuspenseExpenseAccountId()
                : mapping.getDefaultSuspenseRevenueAccountId();

        log.info("No rule or high-confidence AI matched. Assigning to suspense account: {}", suspenseAccountId);

        return CategorizationResult.builder()
                .targetAccountId(suspenseAccountId)
                .confidenceScore(new BigDecimal("0.5000"))
                .source(CategorizationSource.SUSPENSE_FALLBACK)
                .matchedRuleOrReason("Default suspense fallback account")
                .build();
    }
}
