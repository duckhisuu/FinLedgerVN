package vn.finledger.splitbill;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.finledger.ingestion.domain.BankAccountMapping;
import vn.finledger.ingestion.dto.BankWebhookPayload;
import vn.finledger.ingestion.dto.BankWebhookResponse;
import vn.finledger.ingestion.repository.BankAccountMappingRepository;
import vn.finledger.ingestion.service.BankWebhookIngestionService;
import vn.finledger.ledger.domain.Account;
import vn.finledger.ledger.domain.AccountType;
import vn.finledger.ledger.dto.CreateAccountRequest;
import vn.finledger.ledger.repository.AccountRepository;
import vn.finledger.ledger.service.LedgerService;
import vn.finledger.splitbill.calculator.SplitShareInput;
import vn.finledger.splitbill.domain.*;
import vn.finledger.splitbill.dto.DebtSettlementDto;
import vn.finledger.splitbill.repository.DebtSettlementRepository;
import vn.finledger.splitbill.repository.GroupExpenseRepository;
import vn.finledger.splitbill.repository.GroupMemberRepository;
import vn.finledger.splitbill.service.GroupSplitService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
public class GroupSplitAndVietQrIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("finledger_test")
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
    }

    @Autowired
    private GroupSplitService groupSplitService;

    @Autowired
    private BankWebhookIngestionService bankWebhookIngestionService;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private GroupExpenseRepository groupExpenseRepository;

    @Autowired
    private DebtSettlementRepository debtSettlementRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BankAccountMappingRepository bankAccountMappingRepository;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE debt_settlements CASCADE;");
        jdbcTemplate.execute("TRUNCATE TABLE group_expense_shares CASCADE;");
        jdbcTemplate.execute("TRUNCATE TABLE group_expenses CASCADE;");
        jdbcTemplate.execute("TRUNCATE TABLE group_members CASCADE;");
        jdbcTemplate.execute("TRUNCATE TABLE expense_groups CASCADE;");
        jdbcTemplate.execute("TRUNCATE TABLE bank_account_mappings CASCADE;");
        jdbcTemplate.execute("TRUNCATE TABLE webhook_inbox_logs CASCADE;");
    }

    @Test
    @DisplayName("Roommate Expense Splitting: Rent, Electric, Dinner, Min-Cash-Flow & VietQR Auto-Settlement")
    void testRoommatesExpenseSplittingAndVietQrAutoSettlement() throws Exception {
        // 1. Create Roommate Group: 4 Roommates (Hieu, Minh, Tung, Hai)
        ExpenseGroup group = groupSplitService.createGroup("Phong 404", "VND", true);
        UUID groupId = group.getId();

        GroupMember hieu = groupSplitService.addMember(groupId, "Hieu", false, "VCB", "0011002233");
        GroupMember minh = groupSplitService.addMember(groupId, "Minh", false, "TPB", "0987654321");
        GroupMember tung = groupSplitService.addMember(groupId, "Tung", false, "MB", "1122334455");
        GroupMember hai = groupSplitService.addMember(groupId, "Hai", false, "TCB", "9988776655");

        // Setup Bank Account Mapping for Hieu's receiving bank account
        var hieuBankLedgerAccount = ledgerService.createAccount(CreateAccountRequest.builder()
                .name("Hieu Vietcombank")
                .accountType(AccountType.ASSET)
                .currency("VND")
                .allowNegative(true)
                .build());

        var suspenseExpense = ledgerService.createAccount(CreateAccountRequest.builder()
                .name("Default Suspense Expense")
                .accountType(AccountType.EXPENSE)
                .currency("VND")
                .build());

        var suspenseRevenue = ledgerService.createAccount(CreateAccountRequest.builder()
                .name("Default Suspense Revenue")
                .accountType(AccountType.REVENUE)
                .currency("VND")
                .build());

        bankAccountMappingRepository.save(BankAccountMapping.builder()
                .bankAccountNumber("0011002233")
                .bankName("Vietcombank")
                .ledgerAccountId(hieuBankLedgerAccount.getId())
                .defaultSuspenseExpenseAccountId(suspenseExpense.getId())
                .defaultSuspenseRevenueAccountId(suspenseRevenue.getId())
                .build());

        // 2. Expense 1: Room Rent = 4.000.000 paid by Hieu (Split EQUAL for 4)
        List<SplitShareInput> fourMembers = List.of(
                new SplitShareInput(hieu.getId()),
                new SplitShareInput(minh.getId()),
                new SplitShareInput(tung.getId()),
                new SplitShareInput(hai.getId())
        );
        groupSplitService.recordGroupExpense(
                groupId, hieu.getId(), "Tien nha thang 10",
                new BigDecimal("4000000"), SplitStrategy.EQUAL, fourMembers);

        // 3. Expense 2: Electricity = 1.000.000 paid by Minh (Split EQUAL for 4)
        groupSplitService.recordGroupExpense(
                groupId, minh.getId(), "Tien dien sinh hoat",
                new BigDecimal("1000000"), SplitStrategy.EQUAL, fourMembers);

        // 4. Expense 3: Dinner = 100.000 paid by Tung for 3 people (Hieu, Minh, Tung)
        // 100.000 / 3 = 33.333 with remainder 1 -> 33.334, 33.333, 33.333
        List<SplitShareInput> threeMembers = List.of(
                new SplitShareInput(hieu.getId()),
                new SplitShareInput(minh.getId()),
                new SplitShareInput(tung.getId())
        );
        GroupExpense dinnerExpense = groupSplitService.recordGroupExpense(
                groupId, tung.getId(), "An toi bun dau",
                new BigDecimal("100000"), SplitStrategy.EQUAL, threeMembers);

        // Check Hai's net debt balance:
        // Hai participated in Rent (1.000.000) + Electric (250.000) = 1.250.000
        Account haiAccount = accountRepository.findById(hai.getMemberLedgerAccountId()).orElseThrow();
        assertThat(haiAccount.getCachedBalance()).isEqualByComparingTo(new BigDecimal("1250000.0000"));

        // 5. Generate Min-Cash-Flow Settlement Plan
        List<DebtSettlementDto> plan = groupSplitService.generateSettlementPlan(groupId);

        assertThat(plan).isNotEmpty();
        // Number of transactions upper bound: K - 1 <= 3
        assertThat(plan.size()).isLessThanOrEqualTo(3);

        // Find Hai's settlement in the plan (Hai owes 1.250.000)
        DebtSettlementDto haiSettlement = plan.stream()
                .filter(s -> s.getFromMemberId().equals(hai.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Hai should have a settlement to pay his debt"));

        assertThat(haiSettlement.getAmount()).isEqualByComparingTo(new BigDecimal("1250000.0000"));
        assertThat(haiSettlement.getSettlementCode()).startsWith("FLV");
        assertThat(haiSettlement.getVietQrUrl()).contains("compact2.png?amount=1250000&addInfo=" + haiSettlement.getSettlementCode());

        // 6. Simulate Hai scanning VietQR and paying via bank transfer
        // Webhook SePay payload sent with content containing settlement_code
        String rawWebhookPayload = String.format("""
                {
                    "gateway": "vietcombank",
                    "transactionDate": "2026-10-20 12:00:00",
                    "accountNumber": "0011002233",
                    "code": null,
                    "content": "SEPAY %s chuyen tien phong 404",
                    "transferType": "in",
                    "transferAmount": 1250000,
                    "accumulated": 5000000,
                    "subAccount": null,
                    "referenceCode": "MBVCB.123456789",
                    "description": "SEPAY %s chuyen tien phong 404"
                }
                """, haiSettlement.getSettlementCode(), haiSettlement.getSettlementCode());

        BankWebhookPayload webhookPayload = objectMapper.readValue(rawWebhookPayload, BankWebhookPayload.class);
        webhookPayload.setId("TX_HAI_SETTLE_001");

        BankWebhookResponse webhookResponse = bankWebhookIngestionService.processWebhook(
                "sepay", webhookPayload, rawWebhookPayload);

        assertThat(webhookResponse.getStatus()).isEqualTo("PROCESSED");

        // 7. Verify Auto-Settlement completion
        DebtSettlement updatedSettlement = debtSettlementRepository.findById(haiSettlement.getId()).orElseThrow();
        assertThat(updatedSettlement.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(updatedSettlement.getCompletedAt()).isNotNull();
        assertThat(updatedSettlement.getJournalEntryId()).isNotNull();

        // 8. Verify Hai's ledger debt account is now exactly 0.0000!
        Account haiAccountAfterSettlement = accountRepository.findById(hai.getMemberLedgerAccountId()).orElseThrow();
        assertThat(haiAccountAfterSettlement.getCachedBalance())
                .isEqualByComparingTo(BigDecimal.ZERO.setScale(4));
    }
}
