package vn.finledger.ingestion.parsers;

import java.util.Locale;

/**
 * Inbound delivery channel for balance change notifications.
 */
public enum IngestionChannel {
    MOBILE_NOTIFICATION,
    EMAIL,
    WEBHOOK;

    public static IngestionChannel fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return MOBILE_NOTIFICATION;
        }
        String norm = raw.trim().toUpperCase(Locale.ROOT);
        if (norm.equals("MOBILE_PUSH") || norm.equals("PUSH") || norm.equals("OTT")
                || norm.equals("SMS") || norm.equals("MOBILE_NOTIFICATION") || norm.equals("MOBILE")) {
            return MOBILE_NOTIFICATION;
        }
        if (norm.equals("EMAIL") || norm.equals("MAIL") || norm.equals("INBOUND_EMAIL")) {
            return EMAIL;
        }
        if (norm.equals("WEBHOOK") || norm.equals("API")) {
            return WEBHOOK;
        }
        return MOBILE_NOTIFICATION;
    }

    public String fingerprintTag() {
        return switch (this) {
            case MOBILE_NOTIFICATION -> "MOBILE_PUSH";
            case EMAIL -> "EMAIL";
            case WEBHOOK -> "WEBHOOK";
        };
    }
}

