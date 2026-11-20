package vn.finledger.ingestion.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.finledger.auth.security.CurrentUserProvider;
import vn.finledger.ingestion.dedup.MultiSourceIngestionResult;
import vn.finledger.ingestion.dedup.MultiSourceIngestionService;
import vn.finledger.ingestion.dedup.UnauthorizedBridgeException;
import vn.finledger.ingestion.dto.ConnectFinancialSourceRequest;
import vn.finledger.ingestion.dto.ConnectedSourceTelemetryDto;
import vn.finledger.ingestion.dto.InboundEmailAlertRequest;
import vn.finledger.ingestion.dto.MobileBridgeAlertRequest;
import vn.finledger.ingestion.dto.SyncedEventLogDto;
import vn.finledger.ingestion.service.ConnectedSourceService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class VcbMomoIngestionController {

    private final MultiSourceIngestionService multiSourceIngestionService;
    private final ConnectedSourceService connectedSourceService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/api/v1/webhooks/mobile-bridge")
    public ResponseEntity<?> handleMobileBridge(
            @RequestHeader(value = "X-Bridge-Token", required = false) String bridgeToken,
            @RequestBody MobileBridgeAlertRequest request) {
        try {
            MultiSourceIngestionResult result = multiSourceIngestionService.ingestMobileBridgeAlert(
                    bridgeToken,
                    request.getSenderHint(),
                    request.getRawMessage(),
                    request.getAccountIdentifier(),
                    request.getReceivedAt()
            );
            return ResponseEntity.ok(result);
        } catch (UnauthorizedBridgeException e) {
            log.warn("Rejected unauthorized mobile-bridge request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid mobile-bridge payload: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/api/v1/webhooks/inbound-email")
    public ResponseEntity<?> handleInboundEmail(
            @RequestHeader(value = "X-Bridge-Token", required = false) String bridgeToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Email-Token", required = false) String emailTokenHeader,
            @RequestBody InboundEmailAlertRequest request) {
        try {
            String fallbackSecret = emailTokenHeader != null && !emailTokenHeader.isBlank()
                    ? emailTokenHeader
                    : authHeader;
            MultiSourceIngestionResult result = multiSourceIngestionService.ingestInboundEmail(
                    bridgeToken,
                    fallbackSecret,
                    request.getSender(),
                    request.getSubject(),
                    request.getBody(),
                    request.getAccountIdentifier(),
                    request.getReceivedAt()
            );
            return ResponseEntity.ok(result);
        } catch (UnauthorizedBridgeException e) {
            log.warn("Rejected unauthorized inbound-email request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid inbound-email payload: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/api/v1/ingestion/sources")
    public ResponseEntity<List<ConnectedSourceTelemetryDto>> listConnectedSources() {
        UUID userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(connectedSourceService.listUserSources(userId));
    }

    @GetMapping("/api/v1/ingestion/events")
    public ResponseEntity<List<SyncedEventLogDto>> listSyncedEvents() {
        UUID userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(connectedSourceService.listUserSyncedEvents(userId));
    }

    @PostMapping("/api/v1/ingestion/sources")
    public ResponseEntity<ConnectedSourceTelemetryDto> connectSource(
            @Valid @RequestBody ConnectFinancialSourceRequest request) {
        UUID userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(connectedSourceService.connectSource(userId, request));
    }

    @PostMapping("/api/v1/ingestion/sources/rotate-token")
    public ResponseEntity<List<ConnectedSourceTelemetryDto>> rotateBridgeToken() {
        UUID userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(connectedSourceService.rotateUserBridgeToken(userId));
    }
}

