package vn.finledger.ingestion.parsers;

import vn.finledger.ingestion.ocr.VietnameseReceiptLineItemExtractor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared parsing utilities for Vietnamese bank and e-wallet notifications (money normalization & timestamps).
 */
final class VietnameseBankParsingSupport {

    static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("HH:mm dd-MM-yyyy")
    );

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            "(\\d{1,2}[-/]\\d{1,2}[-/]\\d{4}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?|\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?|\\d{1,2}:\\d{2}(?::\\d{2})?\\s+\\d{1,2}[-/]\\d{1,2}[-/]\\d{4})"
    );

    private VietnameseBankParsingSupport() {
    }

    static String normalizeAscii(String input) {
        if (input == null) {
            return "";
        }
        return VietnameseReceiptLineItemExtractor.removeDiacritics(input)
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    static String normalizeUpper(String input) {
        return normalizeAscii(input).toUpperCase(Locale.ROOT);
    }

    /**
     * Parses Vietnamese formatted currency numbers (e.g. "55,000", "45.000", "12,450,000", "500000")
     * into NUMERIC(19, 4) BigDecimal using HALF_EVEN.
     */
    static Optional<BigDecimal> parseVndAmount(String rawNumber) {
        if (rawNumber == null || rawNumber.isBlank()) {
            return Optional.empty();
        }
        String cleaned = rawNumber.trim().replaceAll("\\s+", "");
        // Case like "500,000.00" or "500.000,00" where last group is 2 decimal digits after thousand separator
        if (cleaned.matches("^\\d{1,3}(?:,\\d{3})+\\.\\d{1,2}$")) {
            cleaned = cleaned.replace(",", "");
        } else if (cleaned.matches("^\\d{1,3}(?:\\.\\d{3})+,\\d{1,2}$")) {
            cleaned = cleaned.replace(".", "").replace(",", ".");
        } else {
            // Standard Vietnamese VND formatting: strip both comma and dot thousand separators
            cleaned = cleaned.replace(",", "").replace(".", "");
        }
        try {
            BigDecimal val = new BigDecimal(cleaned).setScale(4, RoundingMode.HALF_EVEN);
            if (val.compareTo(BigDecimal.ZERO) < 0) {
                return Optional.empty();
            }
            return Optional.of(val);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    static Instant extractTimestampOrDefault(String rawMessage, Instant fallback) {
        if (rawMessage != null && !rawMessage.isBlank()) {
            Matcher matcher = TIMESTAMP_PATTERN.matcher(rawMessage);
            if (matcher.find()) {
                String candidate = matcher.group(1).trim().replaceAll("\\s+", " ");
                for (DateTimeFormatter fmt : DATE_TIME_FORMATTERS) {
                    try {
                        LocalDateTime ldt = LocalDateTime.parse(candidate, fmt);
                        return ldt.atZone(VIETNAM_ZONE).toInstant();
                    } catch (Exception ignored) {
                        // Try next formatter
                    }
                }
            }
        }
        return fallback != null ? fallback : Instant.now();
    }

    static String detectLinkedBankProvider(String upperText) {
        if (upperText == null) {
            return null;
        }
        if (upperText.contains("VIETCOMBANK") || upperText.contains("VCB")) {
            return "VCB";
        }
        if (upperText.contains("MOMO")) {
            return "MOMO";
        }
        return null;
    }
}

