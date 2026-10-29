package vn.finledger.finance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.finledger.auth.dto.AuthResponse;
import vn.finledger.auth.dto.LoginRequest;
import vn.finledger.auth.dto.RegisterRequest;
import vn.finledger.finance.dto.*;
import vn.finledger.ledger.domain.Account;
import vn.finledger.ledger.repository.AccountRepository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
public class PersonalFinanceAndAuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("finledger_pf_test")
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
    private AccountRepository accountRepository;

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("End-to-End Personal Finance: JWT Auth, Double-Entry Wallets, Expense/Income/Transfer, Reversal, Budgets & User Isolation")
    void testPersonalFinanceEndToEndAndInvariants() {
        // 1. Unauthenticated request must return 401 Unauthorized
        ResponseEntity<String> unauthRes = restTemplate.getForEntity("/api/v1/finance/overview", String.class);
        assertThat(unauthRes.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 2. Register Customer #1
        RegisterRequest regReq = RegisterRequest.builder()
                .fullName("Nguyễn Minh Hiếu")
                .email("hieu@finledger.vn")
                .password("MatKhauAnToan123")
                .build();

        ResponseEntity<AuthResponse> regRes = restTemplate.postForEntity("/api/v1/auth/register", regReq, AuthResponse.class);
        assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(regRes.getBody()).isNotNull();
        String tokenA = regRes.getBody().getToken();
        UUID userAId = regRes.getBody().getUser().getId();
        assertThat(tokenA).isNotBlank();

        // Login also works
        ResponseEntity<AuthResponse> loginRes = restTemplate.postForEntity(
                "/api/v1/auth/login",
                LoginRequest.builder().email("HIEU@finledger.vn").password("MatKhauAnToan123").build(),
                AuthResponse.class);
        assertThat(loginRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 3. Initial Overview starts clean at 0 VND with default Cash Wallet
        ResponseEntity<HomeOverviewResponse> initOverview = restTemplate.exchange(
                "/api/v1/finance/overview",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(tokenA)),
                HomeOverviewResponse.class);
        assertThat(initOverview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initOverview.getBody().getTotalBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(initOverview.getBody().getWallets()).hasSize(1);
        UUID cashWalletId = initOverview.getBody().getWallets().get(0).getId();

        // 4. Create Vietcombank Wallet with Opening Balance = 20,000,000 VND
        CreateWalletRequest createVcb = CreateWalletRequest.builder()
                .name("Vietcombank")
                .subtype("BANK")
                .bankCode("VCB")
                .accountNumber("9988776655")
                .initialBalance(new BigDecimal("20000000"))
                .includeInTotal(true)
                .build();
        ResponseEntity<WalletDto> vcbRes = restTemplate.exchange(
                "/api/v1/finance/wallets",
                HttpMethod.POST,
                new HttpEntity<>(createVcb, bearerHeaders(tokenA)),
                WalletDto.class);
        assertThat(vcbRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID vcbWalletId = vcbRes.getBody().getId();
        assertThat(vcbRes.getBody().getBalance()).isEqualByComparingTo(new BigDecimal("20000000.0000"));

        // Fetch Categories
        ResponseEntity<CategoryDto[]> catRes = restTemplate.exchange(
                "/api/v1/finance/categories",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(tokenA)),
                CategoryDto[].class);
        List<CategoryDto> categories = List.of(catRes.getBody());
        UUID salaryCatId = categories.stream()
                .filter(c -> "INCOME".equals(c.getType()) && c.getName().contains("Lương"))
                .findFirst().orElseThrow().getId();
        UUID foodCatId = categories.stream()
                .filter(c -> "EXPENSE".equals(c.getType()) && c.getName().contains("Ăn uống"))
                .findFirst().orElseThrow().getId();

        // 5. Record Income (+15,000,000), Expense (-120,000), Transfer (2,000,000 VCB -> Cash)
        restTemplate.exchange(
                "/api/v1/finance/transactions",
                HttpMethod.POST,
                new HttpEntity<>(RecordPersonalTransactionRequest.builder()
                        .type("INCOME")
                        .amount(new BigDecimal("15000000"))
                        .walletId(vcbWalletId)
                        .categoryId(salaryCatId)
                        .description("Lương tháng này")
                        .build(), bearerHeaders(tokenA)),
                PersonalTransactionDto.class);

        ResponseEntity<PersonalTransactionDto> expRes = restTemplate.exchange(
                "/api/v1/finance/transactions",
                HttpMethod.POST,
                new HttpEntity<>(RecordPersonalTransactionRequest.builder()
                        .type("EXPENSE")
                        .amount(new BigDecimal("120000"))
                        .walletId(vcbWalletId)
                        .categoryId(foodCatId)
                        .description("Ăn trưa phở bò")
                        .build(), bearerHeaders(tokenA)),
                PersonalTransactionDto.class);
        UUID expenseTxId = expRes.getBody().getId();

        restTemplate.exchange(
                "/api/v1/finance/transactions",
                HttpMethod.POST,
                new HttpEntity<>(RecordPersonalTransactionRequest.builder()
                        .type("TRANSFER")
                        .amount(new BigDecimal("2000000"))
                        .walletId(vcbWalletId)
                        .toWalletId(cashWalletId)
                        .description("Rút tiền mặt ATM")
                        .build(), bearerHeaders(tokenA)),
                PersonalTransactionDto.class);

        // 6. Verify Overview Balances & Strict Double-Entry Global Zero-Sum Invariant!
        ResponseEntity<HomeOverviewResponse> afterTxOverview = restTemplate.exchange(
                "/api/v1/finance/overview",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(tokenA)),
                HomeOverviewResponse.class);
        HomeOverviewResponse ov = afterTxOverview.getBody();
        assertThat(ov.getTotalBalance()).isEqualByComparingTo(new BigDecimal("34880000.0000"));
        assertThat(ov.getMonthIncome()).isEqualByComparingTo(new BigDecimal("15000000.0000"));
        assertThat(ov.getMonthSpent()).isEqualByComparingTo(new BigDecimal("120000.0000"));
        assertThat(ov.getTopSpendingMonth()).hasSize(1);
        assertThat(ov.getTopSpendingMonth().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("120000.0000"));

        // Verify Algebraic Sum of ALL accounts of User A == 0.0000 (Double-Entry Accounting Equation)
        BigDecimal algebraicSum = accountRepository.findByUserIdOrderByCreatedAtAsc(userAId).stream()
                .map(Account::getCachedBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(algebraicSum).isEqualByComparingTo(BigDecimal.ZERO.setScale(4));

        // 7. Create Monthly Budget for Ăn uống = 1,000,000 -> spent 120,000 (12%)
        String currentMonth = YearMonth.now(vn.finledger.finance.service.PersonalFinanceService.VN_ZONE)
                .format(DateTimeFormatter.ofPattern("yyyy-MM"));
        ResponseEntity<BudgetDto> budgetRes = restTemplate.exchange(
                "/api/v1/finance/budgets",
                HttpMethod.POST,
                new HttpEntity<>(UpsertBudgetRequest.builder()
                        .categoryId(foodCatId)
                        .monthYear(currentMonth)
                        .amountLimit(new BigDecimal("1000000"))
                        .build(), bearerHeaders(tokenA)),
                BudgetDto.class);
        assertThat(budgetRes.getBody().getSpentAmount()).isEqualByComparingTo(new BigDecimal("120000.0000"));
        assertThat(budgetRes.getBody().getRemainingAmount()).isEqualByComparingTo(new BigDecimal("880000.0000"));
        assertThat(budgetRes.getBody().getPercentage()).isEqualByComparingTo(new BigDecimal("12.00"));

        // 8. Cancel (Reverse) Expense -> Append-only reversal restores balance & resets spent to 0!
        restTemplate.exchange(
                "/api/v1/finance/transactions/" + expenseTxId,
                HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(tokenA)),
                String.class);

        ResponseEntity<HomeOverviewResponse> afterCancel = restTemplate.exchange(
                "/api/v1/finance/overview",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(tokenA)),
                HomeOverviewResponse.class);
        assertThat(afterCancel.getBody().getTotalBalance()).isEqualByComparingTo(new BigDecimal("35000000.0000"));
        assertThat(afterCancel.getBody().getMonthSpent()).isEqualByComparingTo(BigDecimal.ZERO);

        // 9. Multi-Tenant Isolation: Register User B -> sees 0 VND and cannot access User A's wallet
        ResponseEntity<AuthResponse> regB = restTemplate.postForEntity(
                "/api/v1/auth/register",
                RegisterRequest.builder()
                        .fullName("Trần Thị Lan")
                        .email("lan@finledger.vn")
                        .password("MatKhauLan456")
                        .build(),
                AuthResponse.class);
        String tokenB = regB.getBody().getToken();

        ResponseEntity<HomeOverviewResponse> ovB = restTemplate.exchange(
                "/api/v1/finance/overview",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(tokenB)),
                HomeOverviewResponse.class);
        assertThat(ovB.getBody().getTotalBalance()).isEqualByComparingTo(BigDecimal.ZERO);

        // User B attempting to spend from User A's wallet must fail with 404 Account Not Found
        ResponseEntity<String> crossAccess = restTemplate.exchange(
                "/api/v1/finance/transactions",
                HttpMethod.POST,
                new HttpEntity<>(RecordPersonalTransactionRequest.builder()
                        .type("EXPENSE")
                        .amount(new BigDecimal("50000"))
                        .walletId(vcbWalletId)
                        .categoryId(foodCatId)
                        .build(), bearerHeaders(tokenB)),
                String.class);
        assertThat(crossAccess.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

