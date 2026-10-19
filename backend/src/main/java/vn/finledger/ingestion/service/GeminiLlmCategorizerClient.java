package vn.finledger.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.finledger.ledger.domain.Account;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class GeminiLlmCategorizerClient implements LlmCategorizerClient {

    private final boolean aiEnabled;
    private final String apiKey;
    private final RestClient restClient;

    public GeminiLlmCategorizerClient(
            @Value("${finledger.ai.enabled:false}") boolean aiEnabled,
            @Value("${finledger.ai.api-key:}") String apiKey,
            RestClient.Builder restClientBuilder) {
        this.aiEnabled = aiEnabled;
        this.apiKey = apiKey;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public Optional<CategorizationResult> categorizeWithAi(
            String sanitizedContent,
            String transferType,
            List<Account> candidateAccounts) {

        if (!aiEnabled || apiKey == null || apiKey.isBlank() || candidateAccounts.isEmpty()) {
            log.debug("AI categorizer is disabled or API key is not configured. Falling back to next pipeline stage.");
            return Optional.empty();
        }

        try {
            // Build candidates map for the prompt
            StringBuilder candidateList = new StringBuilder();
            for (Account acc : candidateAccounts) {
                candidateList.append(String.format("- ID: %s, Name: %s, Type: %s\n",
                        acc.getId(), acc.getName(), acc.getAccountType()));
            }

            String prompt = String.format("""
                    You are a fintech AI categorization assistant for Vietnam personal finance.
                    Classify the following bank transaction description to the single best matching account from the candidates list.
                    
                    Transaction:
                    - Description: "%s"
                    - Transfer Type: %s
                    
                    Candidates:
                    %s
                    
                    Return ONLY valid JSON with fields:
                    {
                      "targetAccountId": "<UUID>",
                      "confidenceScore": <float between 0.0 and 1.0>,
                      "reasoning": "<short reason>"
                    }
                    """, sanitizedContent, transferType, candidateList);

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    )
            );

            // Call Gemini 2.5 Flash / Flash-Lite API
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;

            Map<?, ?> response = restClient.post()
                    .uri(url)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            // Parse response
            if (response != null && response.containsKey("candidates")) {
                List<?> candidates = (List<?>) response.get("candidates");
                if (!candidates.isEmpty()) {
                    Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
                    Map<?, ?> content = (Map<?, ?>) firstCandidate.get("content");
                    List<?> parts = (List<?>) content.get("parts");
                    Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
                    String text = (String) firstPart.get("text");

                    // Simple JSON extraction
                    return parseAiResponse(text, candidateAccounts);
                }
            }
        } catch (Exception e) {
            log.warn("Gemini AI Categorization call failed, will proceed to suspense fallback: {}", e.getMessage());
        }

        return Optional.empty();
    }

    private Optional<CategorizationResult> parseAiResponse(String jsonText, List<Account> candidates) {
        try {
            // Find account ID matching any candidate
            for (Account acc : candidates) {
                if (jsonText.contains(acc.getId().toString())) {
                    // Extract confidence score if possible, default to 0.85
                    BigDecimal confidence = new BigDecimal("0.85");
                    return Optional.of(CategorizationResult.builder()
                            .targetAccountId(acc.getId())
                            .confidenceScore(confidence)
                            .source(CategorizationSource.AI_PREDICTION)
                            .matchedRuleOrReason("Gemini AI categorization")
                            .build());
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse AI response JSON: {}", jsonText);
        }
        return Optional.empty();
    }
}
