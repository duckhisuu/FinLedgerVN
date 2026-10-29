package vn.finledger.splitbill.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.finledger.ledger.domain.Account;
import vn.finledger.ledger.dto.JournalEntryResponse;
import vn.finledger.ledger.repository.AccountRepository;
import vn.finledger.splitbill.domain.DebtSettlement;
import vn.finledger.splitbill.domain.ExpenseGroup;
import vn.finledger.splitbill.domain.GroupMember;
import vn.finledger.splitbill.domain.SettlementStatus;
import vn.finledger.splitbill.dto.*;
import vn.finledger.splitbill.repository.DebtSettlementRepository;
import vn.finledger.splitbill.repository.GroupExpenseShareRepository;
import vn.finledger.splitbill.service.GroupSplitService;
import vn.finledger.splitbill.sse.SettlementSseBroadcaster;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PublicGroupShareControllerTest {

    @Mock
    private GroupSplitService groupSplitService;

    @Mock
    private SettlementSseBroadcaster sseBroadcaster;

    @InjectMocks
    private PublicGroupShareController controller;

    @Test
    @DisplayName("GET /api/v1/public/groups/{shareToken} returns group details, members, and simplified settlements")
    void testGetPublicGroupDetails() {
        UUID groupId = UUID.randomUUID();
        String shareToken = "token123456789";

        PublicGroupShareResponse expected = PublicGroupShareResponse.builder()
                .groupId(groupId)
                .groupName("Phong 404")
                .currency("VND")
                .shareToken(shareToken)
                .simplifyDebts(true)
                .members(List.of(GroupMemberDto.builder()
                        .displayName("Hieu")
                        .netBalance(new BigDecimal("1250000.0000"))
                        .build()))
                .settlements(List.of(DebtSettlementDto.builder()
                        .settlementCode("FLV123ABC")
                        .amount(new BigDecimal("1250000.0000"))
                        .status("PENDING")
                        .build()))
                .build();

        when(groupSplitService.getPublicGroupDetails(shareToken)).thenReturn(expected);

        ResponseEntity<PublicGroupShareResponse> response = controller.getPublicGroupDetails(shareToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PublicGroupShareResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getGroupName()).isEqualTo("Phong 404");
        assertThat(body.getMembers()).hasSize(1);
        assertThat(body.getSettlements()).hasSize(1);
        assertThat(body.getSettlements().get(0).getSettlementCode()).isEqualTo("FLV123ABC");
    }

    @Test
    @DisplayName("GET /api/v1/public/groups/{shareToken}/stream subscribes to SSE")
    void testSubscribeSettlementStream() {
        String shareToken = "token123";
        ExpenseGroup group = ExpenseGroup.builder().id(UUID.randomUUID()).shareToken(shareToken).build();
        when(groupSplitService.getGroupByShareToken(shareToken)).thenReturn(group);

        SseEmitter mockEmitter = new SseEmitter();
        when(sseBroadcaster.subscribe(shareToken)).thenReturn(mockEmitter);

        SseEmitter emitter = controller.subscribeSettlementStream(shareToken);

        assertThat(emitter).isNotNull();
        verify(sseBroadcaster).subscribe(shareToken);
    }

    @Test
    @DisplayName("POST simulate-webhook triggers settlement and returns completed status")
    void testSimulateWebhookSettlement() {
        String shareToken = "token123";
        String code = "FLV999888";

        DebtSettlementDto dto = DebtSettlementDto.builder()
                .id(UUID.randomUUID())
                .fromMemberName("Hai")
                .toMemberName("Hieu")
                .settlementCode(code)
                .amount(new BigDecimal("350000.0000"))
                .status("COMPLETED")
                .build();

        when(groupSplitService.simulateWebhookSettlement(shareToken, code)).thenReturn(dto);

        ResponseEntity<?> response = controller.simulateWebhookSettlement(shareToken, code);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("status")).isEqualTo("COMPLETED");
        assertThat(body.get("settlementCode")).isEqualTo(code);
        verify(groupSplitService).simulateWebhookSettlement(shareToken, code);
    }
}
