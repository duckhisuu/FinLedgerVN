package vn.finledger.splitbill.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.finledger.ledger.domain.Account;
import vn.finledger.ledger.repository.AccountRepository;
import vn.finledger.splitbill.domain.*;
import vn.finledger.splitbill.dto.*;
import vn.finledger.splitbill.repository.DebtSettlementRepository;
import vn.finledger.splitbill.repository.GroupExpenseShareRepository;
import vn.finledger.splitbill.service.GroupSplitService;
import vn.finledger.splitbill.sse.SettlementSseBroadcaster;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/public/groups")
@RequiredArgsConstructor
public class PublicGroupShareController {

    private final GroupSplitService groupSplitService;
    private final SettlementSseBroadcaster sseBroadcaster;

    /**
     * Public endpoint to view group expense settlement details without authentication.
     */
    @GetMapping("/{shareToken}")
    public ResponseEntity<PublicGroupShareResponse> getPublicGroupDetails(@PathVariable String shareToken) {
        return ResponseEntity.ok(groupSplitService.getPublicGroupDetails(shareToken));
    }

    /**
     * Server-Sent Events stream for real-time settlement updates.
     */
    @GetMapping(value = "/{shareToken}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeSettlementStream(@PathVariable String shareToken) {
        // Validate group exists
        groupSplitService.getGroupByShareToken(shareToken);
        return sseBroadcaster.subscribe(shareToken);
    }

    @PostMapping("/{shareToken}/simulate-webhook/{settlementCode}")
    public ResponseEntity<?> simulateWebhookSettlement(
            @PathVariable String shareToken,
            @PathVariable String settlementCode) {

        DebtSettlementDto settled = groupSplitService.simulateWebhookSettlement(shareToken, settlementCode);

        return ResponseEntity.ok(Map.of(
                "status", "COMPLETED",
                "settlementCode", settlementCode,
                "amount", settled.getAmount(),
                "fromMember", settled.getFromMemberName(),
                "toMember", settled.getToMemberName(),
                "message", "Simulated bank webhook payment processed and settled successfully!"
        ));
    }
}
