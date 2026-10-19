package vn.finledger.ingestion.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Slf4j
@Component
public class WebhookSignatureVerifier {

    private final String expectedApiKey;
    private final String expectedHmacSecret;

    public WebhookSignatureVerifier(
            @Value("${finledger.webhook.api-key:finledger_secret_webhook_key_2026}") String expectedApiKey,
            @Value("${finledger.webhook.signature-secret:finledger_hmac_secret_2026}") String expectedHmacSecret) {
        this.expectedApiKey = expectedApiKey;
        this.expectedHmacSecret = expectedHmacSecret;
    }

    /**
     * Constant-time verification of API Key from Authorization header (e.g. "Apikey <secret>" or "<secret>").
     */
    public boolean verifyApiKey(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return false;
        }

        String token = authorizationHeader.trim();
        if (token.toLowerCase().startsWith("apikey ")) {
            token = token.substring(7).trim();
        } else if (token.toLowerCase().startsWith("bearer ")) {
            token = token.substring(7).trim();
        }

        byte[] providedBytes = token.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = expectedApiKey.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(providedBytes, expectedBytes);
    }

    /**
     * Constant-time verification of HMAC-SHA256 signature from X-Webhook-Signature header.
     */
    public boolean verifyHmacSignature(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank() || payload == null) {
            return false;
        }

        try {
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    expectedHmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmacSha256.init(secretKeySpec);

            byte[] hash = hmacSha256.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedHex = HexFormat.of().formatHex(hash);

            byte[] providedBytes = signatureHeader.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
            byte[] expectedBytes = expectedHex.toLowerCase().getBytes(StandardCharsets.UTF_8);

            return MessageDigest.isEqual(providedBytes, expectedBytes);
        } catch (Exception e) {
            log.error("Failed to compute HMAC-SHA256 for webhook payload", e);
            return false;
        }
    }

    /**
     * Validates either API Key or HMAC Signature against incoming request headers.
     */
    public boolean isValidWebhookRequest(String authHeader, String signatureHeader, String rawPayload) {
        if (authHeader != null && !authHeader.isBlank()) {
            if (verifyApiKey(authHeader)) {
                return true;
            }
        }

        if (signatureHeader != null && !signatureHeader.isBlank() && rawPayload != null) {
            return verifyHmacSignature(rawPayload, signatureHeader);
        }

        return false;
    }
}
