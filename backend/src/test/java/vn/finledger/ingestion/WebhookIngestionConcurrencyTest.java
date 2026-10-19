package vn.finledger.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.finledger.ingestion.domain.BankAccountMapping;
import vn.finledger.ingestion.domain.CategorizationRule;
import vn.finledger.ingestion.domain.WebhookInboxLog;
import vn.finledger.ingestion.domain.WebhookInboxStatus;
import vn.finledger.ingestion.dto.BankWebhookPayload;
import vn.finledger.ingestion.dto.BankWebhookResponse;
import vn.finledger.ingestion.repository.BankAccountMappingRepository;
import vn.finledger.ingestion.repository.CategorizationRuleRepository;
import vn.finledger.ingestion.repository.WebhookInboxLogRepository;
import vn.finledger.ingestion.service.BankWebhookIngestionService;
import vn.finledger.ledger.domain.AccountType;
import vn.finledger.ledger.dto.AccountResponse;
import vn.finledger.ledger.dto.CreateAccountRequest;
import vn.finledger.ledger.repository.AccountRepository;
import vn.finledger.ledger.repository.JournalEntryRepository;
import vn.finledger.ledger.service.LedgerService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
public class WebhookIngestionConcurrencyTest {

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
        registry.add("finledger.ai.enabled", () -> "false");
    }

    @Autowired
    private BankWebhookIngestionService ingestionService;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BankAccountMappingRepository mappingRepository;

    @Autowired
    private CategorizationRuleRepository ruleRepository;

    @Autowired
    private WebhookInboxLogRepository inboxLogRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private AccountResponse bankAccount;
    private AccountResponse transportExpenseAccount;
    private AccountResponse suspenseExpenseAccount;
    private AccountResponse suspenseRevenueAccount;
    private BankAccountMapping mapping;

    private static final String BANK_ACC_NUM = "1029384756";

    @BeforeEach
    void setupData() {
        // Clear Redis keys
        try {
            var keys = redisTemplate.keys("idempotency:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignored) {
        }

        // Clean tables in reverse dependency order
        jdbcTemplate.execute("DELETE FROM webhook_inbox_logs");
        jdbcTemplate.execute("DELETE FROM categorization_rules");
        jdbcTemplate.execute("DELETE FROM bank_account_mappings");

        jdbcTemplate.execute("ALTER TABLE journal_entries DISABLE TRIGGER trg_prevent_journal_entries_mutation");
        jdbcTemplate.execute("ALTER TABLE posting_legs DISABLE TRIGGER trg_prevent_posting_legs_mutation");
        jdbcTemplate.execute("DELETE FROM posting_legs");
        jdbcTemplate.execute("DELETE FROM journal_entries");
        jdbcTemplate.execute("ALTER TABLE journal_entries ENABLE TRIGGER trg_prevent_journal_entries_mutation");
        jdbcTemplate.execute("ALTER TABLE posting_legs ENABLE TRIGGER trg_prevent_posting_legs_mutation");
        accountRepository.deleteAll();

        // 1. Create Ledger Accounts
        bankAccount = ledgerService.createAccount(CreateAccountRequest.builder()
                .name("Tài khoản Ngân hàng VCB")
                .accountType(AccountType.ASSET)
                .initialBalance(new BigDecimal("50000000.0000"))
                .allowNegative(false)
                .build());

        transportExpenseAccount = ledgerService.createAccount(CreateAccountRequest.builder()
                .name("Chi phí Đi lại & Taxi")
                .accountType(AccountType.EXPENSE)
                .initialBalance(BigDecimal.ZERO)
                .allowNegative(true)
                .build());

        suspenseExpenseAccount = ledgerService.createAccount(CreateAccountRequest.builder()
                .name("Chi phí Chưa phân loại (Suspense Expense)")
                .accountType(AccountType.EXPENSE)
                .initialBalance(BigDecimal.ZERO)
                .allowNegative(true)
                .build());

        suspenseRevenueAccount = ledgerService.createAccount(CreateAccountRequest.builder()
                .name("Thu nhập Chưa phân loại (Suspense Revenue)")
                .accountType(AccountType.REVENUE)
                .initialBalance(BigDecimal.ZERO)
                .allowNegative(true)
                .build());

        // 2. Create Bank Account Mapping
        mapping = mappingRepository.save(BankAccountMapping.builder()
                .bankAccountNumber(BANK_ACC_NUM)
                .bankName("Vietcombank")
                .ledgerAccountId(bankAccount.getId())
                .defaultSuspenseExpenseAccountId(suspenseExpenseAccount.getId())
                .defaultSuspenseRevenueAccountId(suspenseRevenueAccount.getId())
                .build());

        // 3. Create Rule: "GRAB|BE|XANH SM" -> transportExpenseAccount
        ruleRepository.save(CategorizationRule.builder()
                .keywordPattern("GRAB|BE|XANH SM")
                .transferType("OUT")
                .targetAccountId(transportExpenseAccount.getId())
                .priority(10)
                .active(true)
                .build());
    }

    @Test
    @DisplayName("Test 1: Rule & AI Categorization - Webhook matches GRAB rule and PII phone is sanitized")
    void test1_ruleCategorizationAndPiiSanitizer() throws Exception {
        String rawContent = "NGUYEN VAN A dat xe GRAB di lam 45000 0912345678";
        BigDecimal amount = new BigDecimal("45000.0000");

        BankWebhookPayload payload = BankWebhookPayload.builder()
                .id("TX-SEPAY-GRAB-001")
                .gateway("Vietcombank")
                .accountNumber(BANK_ACC_NUM)
                .content(rawContent)
                .transferType("out")
                .transferAmount(amount)
                .build();

        String rawJson = objectMapper.writeValueAsString(payload);
        BankWebhookResponse response = ingestionService.processWebhook("sepay", payload, rawJson);

        assertThat(response.getStatus()).isEqualTo("PROCESSED");
        assertThat(response.getJournalEntryId()).isNotNull();

        // Verify Balances
        AccountResponse updatedBank = ledgerService.getAccount(bankAccount.getId());
        AccountResponse updatedTransport = ledgerService.getAccount(transportExpenseAccount.getId());

        // Initial Bank 50,000,000 - 45,000 = 49,955,000
        assertThat(updatedBank.getCachedBalance()).isEqualByComparingTo(new BigDecimal("49955000.0000"));
        // Transport Expense increased by 45,000
        assertThat(updatedTransport.getCachedBalance()).isEqualByComparingTo(new BigDecimal("45000.0000"));

        // Verify PII Sanitization in Webhook Inbox Audit Log
        List<WebhookInboxLog> logs = inboxLogRepository.findAll();
        assertThat(logs).hasSize(1);
        WebhookInboxLog auditLog = logs.get(0);
        assertThat(auditLog.getStatus()).isEqualTo(WebhookInboxStatus.PROCESSED);
        assertThat(auditLog.getSanitizedPayload())
                .doesNotContain("0912345678")
                .contains("[REDACTED_PHONE]")
                .contains("GRAB");
    }

    @Test
    @DisplayName("Test 2: Suspense Account Fallback - Unrecognized transaction falls back to default suspense expense account")
    void test2_suspenseAccountFallback() throws Exception {
        String rawContent = "thanh toan dich vu ky la abcxyz 120000";
        BigDecimal amount = new BigDecimal("120000.0000");

        BankWebhookPayload payload = BankWebhookPayload.builder()
                .id("TX-SEPAY-UNKNOWN-002")
                .gateway("Vietcombank")
                .accountNumber(BANK_ACC_NUM)
                .content(rawContent)
                .transferType("out")
                .transferAmount(amount)
                .build();

        String rawJson = objectMapper.writeValueAsString(payload);
        BankWebhookResponse response = ingestionService.processWebhook("sepay", payload, rawJson);

        assertThat(response.getStatus()).isEqualTo("PROCESSED");
        assertThat(response.getJournalEntryId()).isNotNull();

        // Verify Balances
        AccountResponse updatedBank = ledgerService.getAccount(bankAccount.getId());
        AccountResponse updatedSuspense = ledgerService.getAccount(suspenseExpenseAccount.getId());
        AccountResponse updatedTransport = ledgerService.getAccount(transportExpenseAccount.getId());

        // Bank reduced by 120,000
        assertThat(updatedBank.getCachedBalance()).isEqualByComparingTo(new BigDecimal("49880000.0000"));
        // Suspense Expense increased by 120,000
        assertThat(updatedSuspense.getCachedBalance()).isEqualByComparingTo(new BigDecimal("120000.0000"));
        // Transport Expense untouched
        assertThat(updatedTransport.getCachedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Test 3: 20-Thread Webhook Storm Deduplication - Exactly 1 success, 19 DUPLICATE_IGNORED")
    void test3_webhookStormDeduplication() throws Exception {
        int totalThreads = 20;
        String sharedExternalId = "STORM-TX-" + UUID.randomUUID();
        BigDecimal amount = new BigDecimal("200000.0000");

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalThreads);

        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // Synchronize all 20 threads to shoot at the exact same millisecond
                    BankWebhookPayload payload = BankWebhookPayload.builder()
                            .id(sharedExternalId)
                            .gateway("Vietcombank")
                            .accountNumber(BANK_ACC_NUM)
                            .content("Tien an trua dong loat " + sharedExternalId)
                            .transferType("out")
                            .transferAmount(amount)
                            .build();

                    BankWebhookResponse res = ingestionService.processWebhook("sepay", payload, null);
                    if ("PROCESSED".equals(res.getStatus())) {
                        processedCount.incrementAndGet();
                    } else if ("DUPLICATE_IGNORED".equals(res.getStatus())) {
                        duplicateCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown(); // Unleash the 20-thread storm
        boolean finished = endGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("All 20 storm threads should complete within timeout").isTrue();
        assertThat(errorCount.get()).as("Zero unexpected errors should occur").isEqualTo(0);

        // Verification: Exactly 1 processed, 19 duplicate ignored
        assertThat(processedCount.get()).as("Exactly 1 thread should successfully process the webhook").isEqualTo(1);
        assertThat(duplicateCount.get()).as("Exactly 19 threads should be safely ignored as duplicate").isEqualTo(19);

        // Verify Bank Account balance deducted only ONCE: 50,000,000 - 200,000 = 49,800,000
        AccountResponse finalBank = ledgerService.getAccount(bankAccount.getId());
        assertThat(finalBank.getCachedBalance())
                .as("Bank account balance must be deducted only once")
                .isEqualByComparingTo(new BigDecimal("49800000.0000"));

        // Verify exactly one journal entry exists for this referenceId
        String expectedRef = "WEBHOOK:SEPAY:" + sharedExternalId;
        assertThat(journalEntryRepository.findByReferenceId(expectedRef)).isPresent();
    }
}
