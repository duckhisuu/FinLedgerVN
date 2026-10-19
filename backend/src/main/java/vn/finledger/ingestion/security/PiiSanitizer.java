package vn.finledger.ingestion.security;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PiiSanitizer {

    // 16-digit payment card numbers (continuous or separated by space/dash)
    private static final Pattern CARD_PATTERN = Pattern.compile(
            "\\b(?:\\d{4}[ -]?){3}\\d{4}\\b|\\b\\d{16}\\b"
    );

    // Vietnamese phone numbers (+84 or 84 or 03x, 05x, 07x, 08x, 09x followed by 8 digits)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?:\\+?84|0[35789])\\d{8}\\b"
    );

    // Standalone bank account numbers: 8 to 15 digits
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile(
            "\\b\\d{8,15}\\b"
    );

    public String sanitize(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return rawContent;
        }

        // 1. Redact 16-digit credit/debit cards
        Matcher cardMatcher = CARD_PATTERN.matcher(rawContent);
        String step1 = cardMatcher.replaceAll("[REDACTED_CARD]");

        // 2. Redact Vietnamese phone numbers (specifically 10 digits starting with 03/05/07/08/09 or +84)
        Matcher phoneMatcher = PHONE_PATTERN.matcher(step1);
        String step2 = phoneMatcher.replaceAll("[REDACTED_PHONE]");

        // 3. Redact remaining standalone 8 to 15 digit bank account numbers
        Matcher acctMatcher = ACCOUNT_NUMBER_PATTERN.matcher(step2);
        return acctMatcher.replaceAll("[REDACTED_ACCT]");
    }
}
