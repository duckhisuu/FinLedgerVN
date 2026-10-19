package vn.finledger.ingestion.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.finledger.ingestion.dto.BankWebhookPayload;
import vn.finledger.ingestion.dto.BankWebhookResponse;
import vn.finledger.ingestion.security.WebhookSignatureVerifier;
import vn.finledger.ingestion.service.BankWebhookIngestionService;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class BankWebhookController {

    private final WebhookSignatureVerifier signatureVerifier;
    private final BankWebhookIngestionService ingestionService;
    private final ObjectMapper objectMapper;

    @PostMapping("/sepay")
    public ResponseEntity<?> handleSepayWebhook(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signatureHeader,
            @RequestBody String rawPayload) {

        return handleWebhook("sepay", authHeader, signatureHeader, rawPayload);
    }

    @PostMapping("/{provider}")
    public ResponseEntity<?> handleGenericWebhook(
            @PathVariable("provider") String provider,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signatureHeader,
            @RequestBody String rawPayload) {

        return handleWebhook(provider, authHeader, signatureHeader, rawPayload);
    }

    private ResponseEntity<?> handleWebhook(
            String provider,
            String authHeader,
            String signatureHeader,
            String rawPayload) {

        // Constant-time signature & API key security verification
        if (!signatureVerifier.isValidWebhookRequest(authHeader, signatureHeader, rawPayload)) {
            log.warn("Rejected unauthorized webhook request for provider: {}", provider);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: Invalid Webhook Signature or API Key"));
        }

        try {
            BankWebhookPayload payload = objectMapper.readValue(rawPayload, BankWebhookPayload.class);
            BankWebhookResponse response = ingestionService.processWebhook(provider, payload, rawPayload);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Bad request payload from webhook: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal error processing webhook: " + e.getMessage()));
        }
    }
}
