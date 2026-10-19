package vn.finledger.ingestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.finledger.ingestion.domain.BankAccountMapping;
import vn.finledger.ingestion.domain.CategorizationRule;
import vn.finledger.ingestion.ocr.VietnameseReceiptLineItemExtractor;
import vn.finledger.ingestion.repository.CategorizationRuleRepository;
import vn.finledger.ledger.domain.Account;
import vn.finledger.ledger.domain.AccountType;
import vn.finledger.ledger.repository.AccountRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Autonomous, in-memory N-Gram Machine Learning Categorizer for supermarket line items.
 * 100% self-contained without external LLM dependencies.
 * Combines explicit continuous training, database rule-matching, and N-gram similarity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalTrainableCategorizer {

    private final CategorizationRuleRepository categorizationRuleRepository;
    private final AccountRepository accountRepository;

    // In-memory trained mappings: normalized phrase / keyword -> target Account UUID
    private final Map<String, UUID> trainedPhrases = new ConcurrentHashMap<>();

    // Domain keywords for common Vietnamese supermarket categories
    private static final Map<String, List<String>> DOMAIN_CATEGORIES = Map.of(
            "FOOD", List.of("thit", "ca", "heo", "bo", "ga", "rau", "cai", "sua", "trung", "banh", "mi",
                    "gao", "dau an", "muoi", "duong", "nuoc mam", "tom", "muc", "xuc xich", "trai cay",
                    "ca hoi", "fillet", "ba chi", "banh mi", "sandwich", "dau hu", "kho qua", "tra",
                    "ca phe", "sua tuoi", "sua chua", "bia", "nuoc ngot", "snack", "haisan", "rau cu"),
            "HOUSEHOLD", List.of("xa phong", "dau goi", "sua tam", "kem danh rang", "ban chai",
                    "giay ve sinh", "nuoc lau nha", "nuoc rua chen", "bot giat", "khan giay", "tui rac"),
            "HEALTH", List.of("thuoc", "khau trang", "bong bang", "sat trung", "vitamin", "panadol")
    );

    /**
     * Continuously trains the categorizer with a new phrase and target account.
     */
    public void train(String phrase, UUID targetAccountId) {
        if (phrase != null && !phrase.isBlank() && targetAccountId != null) {
            String norm = normalize(phrase);
            trainedPhrases.put(norm, targetAccountId);
            log.info("LocalTrainableCategorizer trained with phrase '{}' -> account {}", norm, targetAccountId);
        }
    }

    /**
     * Categorizes a supermarket product name into a ledger expense account.
     */
    public CategorizationResult categorizeItem(String productName, BankAccountMapping mapping) {
        if (productName == null || productName.isBlank()) {
            return fallbackSuspense(mapping, "Empty product name");
        }

        String normalizedName = normalize(productName);
        Set<String> itemNgrams = generateNgrams(normalizedName);

        // 1. Check in-memory trained dictionary (Exact & N-Gram match)
        UUID bestTrainedAccountId = null;
        double bestTrainedScore = 0.0;
        String bestTrainedPhrase = null;

        for (Map.Entry<String, UUID> entry : trainedPhrases.entrySet()) {
            String trained = entry.getKey();
            if (normalizedName.contains(trained) || trained.contains(normalizedName)) {
                return CategorizationResult.builder()
                        .targetAccountId(entry.getValue())
                        .confidenceScore(new BigDecimal("0.9800"))
                        .source(CategorizationSource.RULE_BASED)
                        .matchedRuleOrReason("Trained exact match: " + trained)
                        .build();
            }
            Set<String> trainedNgrams = generateNgrams(trained);
            double sim = calculateDiceSimilarity(itemNgrams, trainedNgrams);
            if (sim > bestTrainedScore) {
                bestTrainedScore = sim;
                bestTrainedAccountId = entry.getValue();
                bestTrainedPhrase = trained;
            }
        }

        if (bestTrainedAccountId != null && bestTrainedScore >= 0.40) {
            BigDecimal conf = BigDecimal.valueOf(Math.min(0.95, 0.70 + bestTrainedScore * 0.25))
                    .setScale(4, RoundingMode.HALF_EVEN);
            return CategorizationResult.builder()
                    .targetAccountId(bestTrainedAccountId)
                    .confidenceScore(conf)
                    .source(CategorizationSource.AI_PREDICTION)
                    .matchedRuleOrReason("Trained N-gram similarity (" + String.format("%.2f", bestTrainedScore) + ") with: " + bestTrainedPhrase)
                    .build();
        }

        // 2. Check Database Rules (CategorizationRuleRepository)
        try {
            List<CategorizationRule> rules = categorizationRuleRepository.findActiveRulesByTransferType("OUT");
            for (CategorizationRule rule : rules) {
                Pattern pattern = Pattern.compile(rule.getKeywordPattern(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                if (pattern.matcher(productName).find() || pattern.matcher(normalizedName).find()) {
                    return CategorizationResult.builder()
                            .targetAccountId(rule.getTargetAccountId())
                            .confidenceScore(BigDecimal.ONE.setScale(4, RoundingMode.HALF_EVEN))
                            .source(CategorizationSource.RULE_BASED)
                            .matchedRuleOrReason("Database rule: " + rule.getKeywordPattern())
                            .build();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check database categorization rules: {}", e.getMessage());
        }

        // 3. Domain N-Gram Category Heuristics
        List<Account> accounts = accountRepository.findAll();
        for (Map.Entry<String, List<String>> catEntry : DOMAIN_CATEGORIES.entrySet()) {
            String domain = catEntry.getKey();
            for (String kw : catEntry.getValue()) {
                if (normalizedName.contains(kw)) {
                    // Find suitable account in database
                    Optional<Account> matchedAccount = findAccountForDomain(domain, accounts);
                    if (matchedAccount.isPresent()) {
                        return CategorizationResult.builder()
                                .targetAccountId(matchedAccount.get().getId())
                                .confidenceScore(new BigDecimal("0.8500"))
                                .source(CategorizationSource.AI_PREDICTION)
                                .matchedRuleOrReason("Domain N-gram keyword '" + kw + "' -> " + domain)
                                .build();
                    }
                }
            }
        }

        // 4. Default Suspense Fallback
        return fallbackSuspense(mapping, "No matching category found");
    }

    private Optional<Account> findAccountForDomain(String domain, List<Account> accounts) {
        String keyword = switch (domain) {
            case "FOOD" -> "an";
            case "HOUSEHOLD" -> "sinh";
            case "HEALTH" -> "y";
            default -> "chi";
        };

        return accounts.stream()
                .filter(a -> a.getAccountType() == AccountType.EXPENSE)
                .filter(a -> normalize(a.getName()).contains(keyword) || normalize(a.getName()).contains(domain.toLowerCase()))
                .findFirst()
                .or(() -> accounts.stream().filter(a -> a.getAccountType() == AccountType.EXPENSE).findFirst());
    }

    private CategorizationResult fallbackSuspense(BankAccountMapping mapping, String reason) {
        UUID suspenseId = mapping != null && mapping.getDefaultSuspenseExpenseAccountId() != null
                ? mapping.getDefaultSuspenseExpenseAccountId()
                : UUID.randomUUID();

        return CategorizationResult.builder()
                .targetAccountId(suspenseId)
                .confidenceScore(new BigDecimal("0.5000"))
                .source(CategorizationSource.SUSPENSE_FALLBACK)
                .matchedRuleOrReason(reason)
                .build();
    }

    /**
     * Generates unigram and bigram tokens from normalized text.
     */
    public Set<String> generateNgrams(String text) {
        Set<String> ngrams = new HashSet<>();
        if (text == null || text.isBlank()) {
            return ngrams;
        }

        String[] words = text.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            if (!words[i].isBlank()) {
                ngrams.add(words[i]);
            }
            if (i < words.length - 1) {
                ngrams.add(words[i] + " " + words[i + 1]);
            }
        }
        return ngrams;
    }

    /**
     * Calculates Dice coefficient between two n-gram sets.
     */
    public double calculateDiceSimilarity(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() || set2.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        return (2.0 * intersection.size()) / (set1.size() + set2.size());
    }

    private String normalize(String str) {
        return VietnameseReceiptLineItemExtractor.removeDiacritics(str).toLowerCase().trim();
    }
}
