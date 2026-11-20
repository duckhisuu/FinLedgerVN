package vn.finledger.ingestion;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.finledger.auth.dto.AuthResponse;
import vn.finledger.auth.dto.RegisterRequest;
import vn.finledger.ingestion.dedup.MultiSourceIngestionResult;
import vn.finledger.ingestion.domain.BankAccountMapping;
import vn.finledger.ingestion.domain.IngestionFingerprint;
import vn.finledger.ingestion.dto.ConnectFinancialSourceRequest;
import vn.finledger.ingestion.dto.ConnectedSourceTelemetryDto;
import vn.finledger.ingestion.dto.InboundEmailAlertRequest;
import vn.finledger.ingestion.dto.MobileBridgeAlertRequest;
import vn.finledger.ingestion.repository.BankAccountMappingRepository;
import vn.finledger.ingestion.repository.IngestionFingerprintRepository;
import vn.finledger.ledger.domain.Account;
import vn.finledger.ledger.domain.AccountType;
import vn.finledger.ledger.domain.JournalEntry;
import vn.finledger.ledger.repository.AccountRepository;
import vn.finledger.ledger.repository.JournalEntryRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class VcbMomoIngestionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("finledger_vcb_momo_test")
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
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BankAccountMappingRepository mappingRepository;

    @Autowired
    private IngestionFingerprintRepository fingerprintRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    private UUID testUserId;
    private String jwtToken;
    private String bridgeToken;
    private UUID vcbWalletId;
    private UUID momoWalletId;

    @BeforeEach
    void setupCleanUserAndSources() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        jdbcTemplate.execute("ALTER TABLE posting_legs DISABLE TRIGGER trg_prevent_posting_legs_mutation");
        jdbcTemplate.execute("ALTER TABLE journal_entries DISABLE TRIGGER trg_prevent_journal_entries_mutation");
        jdbcTemplate.execute("""
                TRUNCATE TABLE ingestion_fingerprints, webhook_inbox_logs, categorization_rules,
                bank_account_mappings, group_expense_shares, group_expenses, debt_settlements, group_members, expense_groups,
                budgets, posting_legs, journal_entries, accounts, users CASCADE
                """);
        jdbcTemplate.execute("ALTER TABLE posting_legs ENABLE TRIGGER trg_prevent_posting_legs_mutation");
        jdbcTemplate.execute("ALTER TABLE journal_entries ENABLE TRIGGER trg_prevent_journal_entries_mutation");

        try {
            if (redisTemplate.getConnectionFactory() != null) {
                redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
            }
        } catch (Exception ignored) {
        }

        // Register a real user (seeds default VN Expense & Revenue categories automatically)
        RegisterRequest regReq = RegisterRequest.builder()
                .fullName("Minh Khôi VCB MoMo")
                .email("khoi.vcbmomo@finledger.vn")
                .password("TestMatKhau123!")
                .build();
        ResponseEntity<AuthResponse> regRes = restTemplate.postForEntity("/api/v1/auth/register", regReq, AuthResponse.class);
        assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(regRes.getBody()).isNotNull();
        jwtToken = regRes.getBody().getToken();
        testUserId = regRes.getBody().getUser().getId();

        // Connect VCB (initial balance 12,505,000 so after -55,000 => 12,450,000 matching VCB SMS)
        ResponseEntity<ConnectedSourceTelemetryDto> vcbRes = restTemplate.exchange(
                "/api/v1/ingestion/sources",
                HttpMethod.POST,
                new HttpEntity<>(ConnectFinancialSourceRequest.builder()
                        .providerCode("VCB")
                        .accountIdentifier("0011001988888")
                        .walletName("Vietcombank Chính")
                        .initialBalance(new BigDecimal("12505000"))
                        .build(), bearerHeaders(jwtToken)),
                ConnectedSourceTelemetryDto.class
        );
        assertThat(vcbRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(vcbRes.getBody()).isNotNull();
        vcbWalletId = vcbRes.getBody().getWalletId();
        bridgeToken = vcbRes.getBody().getWebhookSecretToken();

        // Connect MoMo (initial balance 365,000 so after -45,000 => 320,000 matching MoMo push)
        ResponseEntity<ConnectedSourceTelemetryDto> momoRes = restTemplate.exchange(
                "/api/v1/ingestion/sources",
                HttpMethod.POST,
                new HttpEntity<>(ConnectFinancialSourceRequest.builder()
                        .providerCode("MOMO")
                        .accountIdentifier("0912345678")
                        .walletName("Ví MoMo Cá Nhân")
                        .initialBalance(new BigDecimal("365000"))
                        .build(), bearerHeaders(jwtToken)),
                ConnectedSourceTelemetryDto.class
        );
        assertThat(momoRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(momoRes.getBody()).isNotNull();
        momoWalletId = momoRes.getBody().getWalletId();
        assertThat(momoRes.getBody().getWebhookSecretToken()).isEqualTo(bridgeToken);
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Parse VCB & MoMo notifications, categorize via LocalTrainableCategorizer, update double-entry & reported balances")
    void testVcbAndMoMoParsingCategorizationAndDoubleEntry() {
        // 1A. Send VCB Push (-55,000 VND lunch)
        MobileBridgeAlertRequest vcbPush = MobileBridgeAlertRequest.builder()
                .senderHint("com.VCB")
                .rawMessage("Số dư TK VCB 0011001988888 thay đổi -55,000 VND lúc 13-11-2026 14:20:10. Số dư 12,450,000 VND. Ref MBVCB.73829102.091234.NGUYEN VAN A chuyen tien an trua.CT tu 0011001988888")
                .build();

        ResponseEntity<MultiSourceIngestionResult> vcbResp = restTemplate.postForEntity(
                "/api/v1/webhooks/mobile-bridge",
                new HttpEntity<>(vcbPush, bridgeHeaders(bridgeToken)),
                MultiSourceIngestionResult.class
        );
        assertThat(vcbResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        MultiSourceIngestionResult vcbBody = vcbResp.getBody();
        assertThat(vcbBody).isNotNull();
        assertThat(vcbBody.getStatus()).isEqualTo("PROCESSED");
        assertThat(vcbBody.getProviderCode()).isEqualTo("VCB");
        assertThat(vcbBody.getAmount()).isEqualByComparingTo("55000.0000");
        assertThat(vcbBody.getRunningBalanceAfterTx()).isEqualByComparingTo("12450000.0000");
        assertThat(vcbBody.getLedgerBalanceAfterTx()).isEqualByComparingTo("12450000.0000");
        assertThat(vcbBody.getExtractedReferenceCode()).isEqualTo("MBVCB.73829102");
        assertThat(vcbBody.getTargetCategoryAccountId()).isNotNull();

        Account vcbAcc = accountRepository.findById(vcbWalletId).orElseThrow();
        assertThat(vcbAcc.getCachedBalance()).isEqualByComparingTo("12450000.0000");

        Account foodCat = accountRepository.findById(vcbBody.getTargetCategoryAccountId()).orElseThrow();
        assertThat(foodCat.getAccountType()).isEqualTo(AccountType.EXPENSE);
        assertThat(foodCat.getCachedBalance()).isEqualByComparingTo("55000.0000");

        // 1B. Send MoMo Push (-45.000đ Circle K)
        MobileBridgeAlertRequest momoPush = MobileBridgeAlertRequest.builder()
                .senderHint("MoMo")
                .rawMessage("Bạn vừa thanh toán 45.000đ cho CIRCLE K VIETNAM. Mã GD: 68291048291. Số dư ví: 320.000đ. Nội dung: Mua nuoc ngot")
                .build();

        ResponseEntity<MultiSourceIngestionResult> momoResp = restTemplate.postForEntity(
                "/api/v1/webhooks/mobile-bridge",
                new HttpEntity<>(momoPush, bridgeHeaders(bridgeToken)),
                MultiSourceIngestionResult.class
        );
        assertThat(momoResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        MultiSourceIngestionResult momoBody = momoResp.getBody();
        assertThat(momoBody).isNotNull();
        assertThat(momoBody.getStatus()).isEqualTo("PROCESSED");
        assertThat(momoBody.getProviderCode()).isEqualTo("MOMO");
        assertThat(momoBody.getAmount()).isEqualByComparingTo("45000.0000");
        assertThat(momoBody.getRunningBalanceAfterTx()).isEqualByComparingTo("320000.0000");
        assertThat(momoBody.getLedgerBalanceAfterTx()).isEqualByComparingTo("320000.0000");
        assertThat(momoBody.getExtractedReferenceCode()).isEqualTo("68291048291");

        BankAccountMapping vcbMap = mappingRepository.findByLedgerAccountId(vcbWalletId).orElseThrow();
        assertThat(vcbMap.getLastReportedBalance()).isEqualByComparingTo("12450000.0000");
        BankAccountMapping momoMap = mappingRepository.findByLedgerAccountId(momoWalletId).orElseThrow();
        assertThat(momoMap.getLastReportedBalance()).isEqualByComparingTo("320000.0000");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Multi-source Fuzzy Deduplication merges Mobile Push + Ref-less Email within 5m without double posting")
    void testMultiSourceFuzzyDeduplicationPushPlusEmail() {
        // Push at T0 = 13-11-2026 14:20:10 (+07:00) = 2026-11-13T07:20:10Z
        MobileBridgeAlertRequest pushReq = MobileBridgeAlertRequest.builder()
                .senderHint("VCB")
                .rawMessage("Số dư TK VCB 0011001988888 thay đổi -55,000 VND lúc 13-11-2026 14:20:10. Số dư 12,450,000 VND. Ref MBVCB.73829102.091234.NGUYEN VAN A chuyen tien an trua")
                .build();

        ResponseEntity<MultiSourceIngestionResult> res1 = restTemplate.postForEntity(
                "/api/v1/webhooks/mobile-bridge",
                new HttpEntity<>(pushReq, bridgeHeaders(bridgeToken)),
                MultiSourceIngestionResult.class
        );
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res1.getBody()).isNotNull();
        assertThat(res1.getBody().getStatus()).isEqualTo("PROCESSED");
        UUID firstEntryId = res1.getBody().getJournalEntryId();

        // Email arrives 2 minutes later (T0 + 2m) WITHOUT Ref code
        InboundEmailAlertRequest emailReq = InboundEmailAlertRequest.builder()
                .sender("VCBDigibank@vietcombank.com.vn")
                .subject("Thông báo biến động số dư tài khoản 0011001988888")
                .body("Tài khoản thanh toán TK VCB 0011001988888 thay đổi -55,000 VND lúc 13-11-2026 14:22:10. Số dư 12,450,000 VND. Nội dung: NGUYEN VAN A chuyen tien an trua")
                .receivedAt(Instant.parse("2026-11-13T07:22:10Z"))
                .build();

        ResponseEntity<MultiSourceIngestionResult> res2 = restTemplate.postForEntity(
                "/api/v1/webhooks/inbound-email",
                new HttpEntity<>(emailReq, bridgeHeaders(bridgeToken)),
                MultiSourceIngestionResult.class
        );
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res2.getBody()).isNotNull();
        assertThat(res2.getBody().getStatus()).isEqualTo("MERGED_DUPLICATE");
        assertThat(res2.getBody().getJournalEntryId()).isEqualTo(firstEntryId);
        assertThat(res2.getBody().getSourcesMerged()).contains("MOBILE_PUSH", "EMAIL");

        // Verify ONLY 1 EXPENSE JournalEntry exists and VCB balance deducted ONCE (12,505,000 - 55,000 = 12,450,000)
        List<JournalEntry> expenseEntries = journalEntryRepository.findAll().stream()
                .filter(je -> "EXPENSE".equals(je.getEntryType()))
                .toList();
        assertThat(expenseEntries).hasSize(1);

        Account vcbAcc = accountRepository.findById(vcbWalletId).orElseThrow();
        assertThat(vcbAcc.getCachedBalance()).isEqualByComparingTo("12450000.0000");

        List<IngestionFingerprint> fps = fingerprintRepository.findByJournalEntryId(firstEntryId);
        assertThat(fps).hasSize(1);
        assertThat(fps.get(0).getSourcesMerged()).isEqualTo("MOBILE_PUSH,EMAIL");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Auto Internal Transfer (VCB -> MoMo and MoMo -> VCB) posts 1 TRANSFER entry with EXPENSE=0 and REVENUE=0")
    void testAutoInternalTransferVcbAndMoMoBothDirections() {
        // 3A. Adjust VCB reported baseline scenario: send VCB -500,000 first, then MoMo +500,000
        // Starting balances: VCB = 12,505,000 | MoMo = 365,000
        MobileBridgeAlertRequest vcbOut = MobileBridgeAlertRequest.builder()
                .senderHint("VCB")
                .rawMessage("SD TK 0011001988888 -500,000VND luc 13-11-2026 14:25:00. SD 12,005,000VND. Ref ECOM261113.MOMO.Nap tien vi MoMo")
                .build();

        ResponseEntity<MultiSourceIngestionResult> r1 = restTemplate.postForEntity(
                "/api/v1/webhooks/mobile-bridge",
                new HttpEntity<>(vcbOut, bridgeHeaders(bridgeToken)),
                MultiSourceIngestionResult.class
        );
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r1.getBody()).isNotNull();
        assertThat(r1.getBody().getStatus()).isEqualTo("INTERNAL_TRANSFER_CREATED");
        UUID transferJournalId = r1.getBody().getJournalEntryId();

        // MoMo counterpart arrives right after
        MobileBridgeAlertRequest momoIn = MobileBridgeAlertRequest.builder()
                .senderHint("MoMo")
                .rawMessage("Nạp tiền thành công 500.000đ vào Ví MoMo từ nguồn tiền Vietcombank. Mã GD: 68291055555. Số dư: 865.000đ")
                .build();

        ResponseEntity<MultiSourceIngestionResult> r2 = restTemplate.postForEntity(
                "/api/v1/webhooks/mobile-bridge",
                new HttpEntity<>(momoIn, bridgeHeaders(bridgeToken)),
                MultiSourceIngestionResult.class
        );
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r2.getBody()).isNotNull();
        assertThat(r2.getBody().getStatus()).isEqualTo("INTERNAL_TRANSFER_LINKED");
        assertThat(r2.getBody().getJournalEntryId()).isEqualTo(transferJournalId);

        // Verify strictly 1 TRANSFER JournalEntry (no duplicate, zero EXPENSE, zero REVENUE)
        List<JournalEntry> allEntries = journalEntryRepository.findByUserIdOrderByTransactionDateDescCreatedAtDesc(testUserId);
        List<JournalEntry> transfers = allEntries.stream()
                .filter(je -> "TRANSFER".equals(je.getEntryType()))
                .toList();
        assertThat(transfers).hasSize(1);

        List<Account> userAccounts = accountRepository.findByUserIdOrderByCreatedAtAsc(testUserId);
        for (Account acc : userAccounts) {
            if (acc.getAccountType() == AccountType.EXPENSE || acc.getAccountType() == AccountType.REVENUE) {
                assertThat(acc.getCachedBalance()).isEqualByComparingTo("0.0000");
            }
        }

        // VCB = 12,505,000 - 500,000 = 12,005,000 ; MoMo = 365,000 + 500,000 = 865,000
        assertThat(accountRepository.findById(vcbWalletId).orElseThrow().getCachedBalance())
                .isEqualByComparingTo("12005000.0000");
        assertThat(accountRepository.findById(momoWalletId).orElseThrow().getCachedBalance())
                .isEqualByComparingTo("865000.0000");

        // 3B. Also verify reverse arrival order (MoMo push arrives FIRST, then VCB push arrives SECOND for 200,000)
        MobileBridgeAlertRequest momoFirst = MobileBridgeAlertRequest.builder()
                .senderHint("MoMo")
                .rawMessage("Nạp tiền thành công 200.000đ vào Ví MoMo từ nguồn tiền Vietcombank. Mã GD: 68291077777. Số dư: 1.065.000đ")
                .build();
        ResponseEntity<MultiSourceIngestionResult> rev1 = restTemplate.postForEntity(
                "/api/v1/webhooks/mobile-bridge",
                new HttpEntity<>(momoFirst, bridgeHeaders(bridgeToken)),
                MultiSourceIngestionResult.class
        );
        assertThat(rev1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rev1.getBody().getStatus()).isEqualTo("INTERNAL_TRANSFER_CREATED");

        MobileBridgeAlertRequest vcbSecond = MobileBridgeAlertRequest.builder()
                .senderHint("VCB")
                .rawMessage("SD TK 0011001988888 -200,000VND luc 13-11-2026 14:35:00. SD 11,805,000VND. Ref ECOM261114.MOMO.Nap tien vi MoMo")
                .build();
        ResponseEntity<MultiSourceIngestionResult> rev2 = restTemplate.postForEntity(
                "/api/v1/webhooks/mobile-bridge",
                new HttpEntity<>(vcbSecond, bridgeHeaders(bridgeToken)),
                MultiSourceIngestionResult.class
        );
        assertThat(rev2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rev2.getBody().getStatus()).isEqualTo("INTERNAL_TRANSFER_LINKED");
        assertThat(rev2.getBody().getJournalEntryId()).isEqualTo(rev1.getBody().getJournalEntryId());

        assertThat(accountRepository.findById(vcbWalletId).orElseThrow().getCachedBalance())
                .isEqualByComparingTo("11805000.0000");
        assertThat(accountRepository.findById(momoWalletId).orElseThrow().getCachedBalance())
                .isEqualByComparingTo("1065000.0000");
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Reconciliation delta verification via GET /api/v1/ingestion/sources & HTTP 401 when X-Bridge-Token missing/invalid")
    void testReconciliationDeltaAndBridgeTokenSecurity() {
        // Send synced VCB + MoMo transactions so both lastReportedBalance fields are populated
        restTemplate.postForEntity(
                "/api/v1/webhooks/mobile-bridge",
                new HttpEntity<>(MobileBridgeAlertRequest.builder()
                        .senderHint("VCB")
                        .rawMessage("Số dư TK VCB 0011001988888 thay đổi -55,000 VND lúc 13-11-2026 14:20:10. Số dư 12,450,000 VND. Ref MBVCB.99887766.an trua")
                        .build(), bridgeHeaders(bridgeToken)),
                MultiSourceIngestionResult.class
        );
        restTemplate.postForEntity(
                "/api/v1/webhooks/mobile-bridge",
                new HttpEntity<>(MobileBridgeAlertRequest.builder()
                        .senderHint("MoMo")
                        .rawMessage("Bạn vừa thanh toán 45.000đ cho CIRCLE K VIETNAM. Mã GD: 68291048291. Số dư ví: 320.000đ. Nội dung: Mua nuoc ngot")
                        .build(), bridgeHeaders(bridgeToken)),
                MultiSourceIngestionResult.class
        );

        ResponseEntity<List<ConnectedSourceTelemetryDto>> sourcesResp = restTemplate.exchange(
                "/api/v1/ingestion/sources",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(jwtToken)),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(sourcesResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ConnectedSourceTelemetryDto> sources = sourcesResp.getBody();
        assertThat(sources).hasSize(2);

        for (ConnectedSourceTelemetryDto src : sources) {
            assertThat(src.getReconciliationDelta()).isEqualByComparingTo("0.0000");
            assertThat(src.isReconciled()).isTrue();
            assertThat(src.getLastReportedBalance()).isEqualByComparingTo(src.getLedgerBalance());
        }

        // Security Test: Missing X-Bridge-Token -> HTTP 401
        MobileBridgeAlertRequest validPayload = MobileBridgeAlertRequest.builder()
                .senderHint("VCB")
                .rawMessage("SD TK 0011001988888 -10,000VND luc 13-11-2026 15:00:00. SD 12,440,000VND. Ref FT123456")
                .build();
        HttpHeaders noTokenHeaders = new HttpHeaders();
        noTokenHeaders.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> missingTokenRes = restTemplate.postForEntity(
                "/api/v1/webhooks/mobile-bridge",
                new HttpEntity<>(validPayload, noTokenHeaders),
                Map.class
        );
        assertThat(missingTokenRes.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Security Test: Wrong X-Bridge-Token -> HTTP 401
        ResponseEntity<Map> wrongTokenRes = restTemplate.postForEntity(
                "/api/v1/webhooks/mobile-bridge",
                new HttpEntity<>(validPayload, bridgeHeaders("wrong_invalid_token_999")),
                Map.class
        );
        assertThat(wrongTokenRes.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private HttpHeaders bridgeHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Bridge-Token", token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}

