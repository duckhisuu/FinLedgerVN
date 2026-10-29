package vn.finledger.splitbill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.finledger.splitbill.calculator.SplitShareInput;
import vn.finledger.splitbill.controller.PublicGroupShareController;
import vn.finledger.splitbill.domain.DebtSettlement;
import vn.finledger.splitbill.domain.ExpenseGroup;
import vn.finledger.splitbill.domain.GroupMember;
import vn.finledger.splitbill.domain.SettlementStatus;
import vn.finledger.splitbill.domain.SplitStrategy;
import vn.finledger.splitbill.dto.DebtSettlementDto;
import vn.finledger.splitbill.dto.PublicGroupShareResponse;
import vn.finledger.splitbill.repository.DebtSettlementRepository;
import vn.finledger.splitbill.service.GroupSplitService;
import vn.finledger.splitbill.sse.SettlementSseBroadcaster;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
public class PublicGroupShareSseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("finledger_sse_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("finledger.demo.seed", () -> "false");
    }

    @Autowired
    private PublicGroupShareController publicGroupShareController;

    @Autowired
    private GroupSplitService groupSplitService;

    @Autowired
    private SettlementSseBroadcaster sseBroadcaster;

    @Autowired
    private DebtSettlementRepository debtSettlementRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE debt_settlements, group_expense_shares, group_expenses, group_members, expense_groups, webhook_inbox_logs, categorization_rules, bank_account_mappings CASCADE;");
        jdbcTemplate.execute("ALTER TABLE journal_entries DISABLE TRIGGER trg_prevent_journal_entries_mutation;");
        jdbcTemplate.execute("ALTER TABLE posting_legs DISABLE TRIGGER trg_prevent_posting_legs_mutation;");
        jdbcTemplate.execute("DELETE FROM posting_legs;");
        jdbcTemplate.execute("DELETE FROM journal_entries;");
        jdbcTemplate.execute("ALTER TABLE journal_entries ENABLE TRIGGER trg_prevent_journal_entries_mutation;");
        jdbcTemplate.execute("ALTER TABLE posting_legs ENABLE TRIGGER trg_prevent_posting_legs_mutation;");
        jdbcTemplate.execute("DELETE FROM accounts;");
    }

    @Test
    @DisplayName("Public Share Details, SSE Stream Subscription & Simulated Webhook Auto-Settlement")
    void testPublicShareAndSimulatedSettlement() {
        // 1. Create Group with 2 members: Hieu (creditor) and Hai (debtor)
        ExpenseGroup group = groupSplitService.createGroup("Public Share Demo Group", "VND", true);
        String shareToken = group.getShareToken();

        GroupMember hieu = groupSplitService.addMember(group.getId(), "Hieu", false, "VCB", "0011002233");
        GroupMember hai = groupSplitService.addMember(group.getId(), "Hai", false, "TCB", "9988776655");

        // 2. Record Expense: Hieu pays 500.000 VND for both -> Hai owes Hieu 250.000 VND
        groupSplitService.recordGroupExpense(
                group.getId(), hieu.getId(), "Tien an toi hai nguoi",
                new BigDecimal("500000.0000"), SplitStrategy.EQUAL,
                List.of(new SplitShareInput(hieu.getId()), new SplitShareInput(hai.getId()))
        );

        // 3. Test GET Public Group Details
        ResponseEntity<PublicGroupShareResponse> detailsResponse =
                publicGroupShareController.getPublicGroupDetails(shareToken);

        assertThat(detailsResponse.getStatusCode().is2xxSuccessful()).isTrue();
        PublicGroupShareResponse details = detailsResponse.getBody();
        assertThat(details).isNotNull();
        assertThat(details.getGroupName()).isEqualTo("Public Share Demo Group");
        assertThat(details.getSettlements()).hasSize(1);

        DebtSettlementDto settlement = details.getSettlements().get(0);
        assertThat(settlement.getAmount()).isEqualByComparingTo(new BigDecimal("250000.0000"));
        assertThat(settlement.getFromMemberName()).isEqualTo("Hai");
        assertThat(settlement.getToMemberName()).isEqualTo("Hieu");
        assertThat(settlement.getSettlementCode()).startsWith("FLV");
        assertThat(settlement.getVietQrUrl()).contains("VCB-0011002233-compact2.png");

        // 4. Test SSE Stream Subscription
        SseEmitter emitter = publicGroupShareController.subscribeSettlementStream(shareToken);
        assertThat(emitter).isNotNull();
        assertThat(sseBroadcaster.getSubscriberCount(shareToken)).isGreaterThanOrEqualTo(1);

        // 5. Test Simulated Bank Webhook Settlement
        ResponseEntity<?> simResponse = publicGroupShareController.simulateWebhookSettlement(
                shareToken, settlement.getSettlementCode());

        assertThat(simResponse.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> simBody = (Map<?, ?>) simResponse.getBody();
        assertThat(simBody.get("status")).isEqualTo("COMPLETED");

        // Verify status in DB
        DebtSettlement updated = debtSettlementRepository.findBySettlementCode(settlement.getSettlementCode()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(updated.getJournalEntryId()).isNotNull();
        assertThat(updated.getCompletedAt()).isNotNull();
    }
}
