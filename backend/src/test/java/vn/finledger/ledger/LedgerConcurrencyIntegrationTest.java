package vn.finledger.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.finledger.ledger.domain.AccountType;
import vn.finledger.ledger.dto.AccountResponse;
import vn.finledger.ledger.dto.CreateAccountRequest;
import vn.finledger.ledger.dto.JournalEntryResponse;
import vn.finledger.ledger.dto.PostingLegRequest;
import vn.finledger.ledger.dto.RecordTransactionRequest;
import vn.finledger.ledger.exception.InsufficientFundsException;
import vn.finledger.ledger.exception.UnbalancedTransactionException;
import vn.finledger.ledger.repository.AccountRepository;
import vn.finledger.ledger.repository.JournalEntryRepository;
import vn.finledger.ledger.repository.PostingLegRepository;
import vn.finledger.ledger.service.LedgerService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
public class LedgerConcurrencyIntegrationTest {

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
    private LedgerService ledgerService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private PostingLegRepository postingLegRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        // Delete child tables first to satisfy foreign key constraints
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
    }

    @Test
    @DisplayName("Test 1: Double-Entry Invariant - Non-zero sum or zero-amount legs must be rejected")
    void test1_doubleEntryInvariant() {
        AccountResponse accA = ledgerService.createAccount(CreateAccountRequest.builder()
                .name("Tài khoản A")
                .accountType(AccountType.ASSET)
                .initialBalance(new BigDecimal("1000000.0000"))
                .allowNegative(false)
                .build());

        AccountResponse accB = ledgerService.createAccount(CreateAccountRequest.builder()
                .name("Tài khoản B")
                .accountType(AccountType.ASSET)
                .initialBalance(BigDecimal.ZERO)
                .allowNegative(false)
                .build());

        // 1. Unbalanced transaction: sum = -50,000 != 0
        RecordTransactionRequest unbalancedRequest = RecordTransactionRequest.builder()
                .description("Giao dịch không cân bằng")
                .referenceId("TX-UNBALANCED-01")
                .postingLegs(List.of(
                        PostingLegRequest.builder()
                                .accountId(accA.getId())
                                .amount(new BigDecimal("-100000.0000"))
                                .build(),
                        PostingLegRequest.builder()
                                .accountId(accB.getId())
                                .amount(new BigDecimal("50000.0000"))
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> ledgerService.recordTransaction(unbalancedRequest))
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessageContaining("Transaction is unbalanced");

        // 2. Zero-amount leg: rejected
        RecordTransactionRequest zeroLegRequest = RecordTransactionRequest.builder()
                .description("Giao dịch có chân bằng 0")
                .referenceId("TX-ZERO-LEG-01")
                .postingLegs(List.of(
                        PostingLegRequest.builder()
                                .accountId(accA.getId())
                                .amount(BigDecimal.ZERO)
                                .build(),
                        PostingLegRequest.builder()
                                .accountId(accB.getId())
                                .amount(BigDecimal.ZERO)
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> ledgerService.recordTransaction(zeroLegRequest))
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessageContaining("Posting leg amount cannot be zero");

        // 3. Single-leg transaction (< 2 legs): rejected
        RecordTransactionRequest singleLegRequest = RecordTransactionRequest.builder()
                .description("Giao dịch 1 chân")
                .referenceId("TX-SINGLE-LEG-01")
                .postingLegs(List.of(
                        PostingLegRequest.builder()
                                .accountId(accA.getId())
                                .amount(new BigDecimal("100000.0000"))
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> ledgerService.recordTransaction(singleLegRequest))
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessageContaining("at least two posting legs");
    }

    @Test
    @DisplayName("Test 2: Immutability Trigger - UPDATE and DELETE on posting_legs must be blocked by DB trigger")
    void test2_immutabilityTrigger() {
        AccountResponse accA = ledgerService.createAccount(CreateAccountRequest.builder()
                .name("Tài khoản A")
                .accountType(AccountType.ASSET)
                .initialBalance(new BigDecimal("1000000.0000"))
                .allowNegative(false)
                .build());

        AccountResponse accB = ledgerService.createAccount(CreateAccountRequest.builder()
                .name("Tài khoản B")
                .accountType(AccountType.ASSET)
                .initialBalance(BigDecimal.ZERO)
                .allowNegative(false)
                .build());

        RecordTransactionRequest validRequest = RecordTransactionRequest.builder()
                .description("Giao dịch hợp lệ")
                .referenceId("TX-VALID-IMMUTABLE")
                .postingLegs(List.of(
                        PostingLegRequest.builder()
                                .accountId(accA.getId())
                                .amount(new BigDecimal("-200000.0000"))
                                .build(),
                        PostingLegRequest.builder()
                                .accountId(accB.getId())
                                .amount(new BigDecimal("200000.0000"))
                                .build()
                ))
                .build();

        JournalEntryResponse recorded = ledgerService.recordTransaction(validRequest);
        UUID legId = recorded.getPostingLegs().get(0).getId();

        // 1. Attempt UPDATE directly via SQL
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE posting_legs SET amount = 99999.0000 WHERE id = ?", legId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Ledger entries are immutable");

        // 2. Attempt DELETE directly via SQL
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM posting_legs WHERE id = ?", legId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Ledger entries are immutable");

        // 3. Attempt UPDATE on journal_entries header
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE journal_entries SET description = 'Hacked' WHERE id = ?", recorded.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Ledger entries are immutable");
    }

    @Test
    @DisplayName("Test 3: High-Concurrency Race Condition & Deadlock Test (50 Threads, 33 Success, 17 InsufficientFunds)")
    void test3_highConcurrencyRaceCondition() throws InterruptedException {
        // Setup: Account A has 10,000,000 VND (allow_negative = false), B has 0 VND, C has 0 VND
        AccountResponse accA = ledgerService.createAccount(CreateAccountRequest.builder()
                .name("Tài khoản A (Quỹ chính)")
                .accountType(AccountType.ASSET)
                .initialBalance(new BigDecimal("10000000.0000"))
                .allowNegative(false)
                .build());

        AccountResponse accB = ledgerService.createAccount(CreateAccountRequest.builder()
                .name("Tài khoản B (Thụ hưởng)")
                .accountType(AccountType.ASSET)
                .initialBalance(new BigDecimal("0.0000"))
                .allowNegative(false)
                .build());

        AccountResponse accC = ledgerService.createAccount(CreateAccountRequest.builder()
                .name("Tài khoản C (Dự phòng)")
                .accountType(AccountType.ASSET)
                .initialBalance(new BigDecimal("0.0000"))
                .allowNegative(false)
                .build());

        int totalThreads = 50;
        BigDecimal transferAmount = new BigDecimal("300000.0000");

        ExecutorService executor = Executors.newFixedThreadPool(25);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientFundsCount = new AtomicInteger(0);
        AtomicInteger otherErrors = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await(); // Synchronize all 50 threads to hit at the exact same instant
                    RecordTransactionRequest request = RecordTransactionRequest.builder()
                            .description("Concurrent Transfer #" + index)
                            .referenceId("TX-CONC-" + UUID.randomUUID())
                            .postingLegs(List.of(
                                    PostingLegRequest.builder()
                                            .accountId(accA.getId())
                                            .amount(transferAmount.negate())
                                            .build(),
                                    PostingLegRequest.builder()
                                            .accountId(accB.getId())
                                            .amount(transferAmount)
                                            .build()
                            ))
                            .build();

                    ledgerService.recordTransaction(request);
                    successCount.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    insufficientFundsCount.incrementAndGet();
                } catch (Exception e) {
                    otherErrors.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown(); // Unleash all 50 threads
        boolean finished = endGate.await(45, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("All 50 threads should finish within timeout").isTrue();
        assertThat(otherErrors.get()).as("There should be no unexpected errors").isEqualTo(0);

        // Verification according to requirements:
        // Initial balance: 10,000,000 VND
        // Each transfer: 300,000 VND
        // 33 * 300,000 = 9,900,000 VND
        // Remaining: 100,000 VND (< 300,000 => exactly 17 fail)
        assertThat(successCount.get()).as("Exactly 33 transactions must succeed").isEqualTo(33);
        assertThat(insufficientFundsCount.get()).as("Exactly 17 transactions must fail with InsufficientFundsException").isEqualTo(17);

        AccountResponse finalAccA = ledgerService.getAccount(accA.getId());
        AccountResponse finalAccB = ledgerService.getAccount(accB.getId());
        AccountResponse finalAccC = ledgerService.getAccount(accC.getId());

        assertThat(finalAccA.getCachedBalance())
                .as("Final balance of Account A must be exactly 100,000.0000 VND")
                .isEqualByComparingTo(new BigDecimal("100000.0000"));

        assertThat(finalAccB.getCachedBalance())
                .as("Final balance of Account B must be exactly 9,900,000.0000 VND")
                .isEqualByComparingTo(new BigDecimal("9900000.0000"));

        assertThat(finalAccC.getCachedBalance())
                .as("Final balance of Account C must remain 0.0000 VND")
                .isEqualByComparingTo(BigDecimal.ZERO);

        // Conservation of Value Invariant: Total system balance = Initial total balance
        BigDecimal totalSystemBalance = finalAccA.getCachedBalance()
                .add(finalAccB.getCachedBalance())
                .add(finalAccC.getCachedBalance());
        assertThat(totalSystemBalance).isEqualByComparingTo(new BigDecimal("10000000.0000"));
    }

    @Test
    @DisplayName("Test 4: Cross-Transfer Deadlock Freedom (20 Threads A->B, 20 Threads B->A)")
    void test4_crossTransferDeadlockFreedom() throws InterruptedException {
        // Setup: Account A has 10,000,000 VND, Account B has 10,000,000 VND
        AccountResponse accA = ledgerService.createAccount(CreateAccountRequest.builder()
                .name("Tài khoản A (Đối ứng)")
                .accountType(AccountType.ASSET)
                .initialBalance(new BigDecimal("10000000.0000"))
                .allowNegative(false)
                .build());

        AccountResponse accB = ledgerService.createAccount(CreateAccountRequest.builder()
                .name("Tài khoản B (Đối ứng)")
                .accountType(AccountType.ASSET)
                .initialBalance(new BigDecimal("10000000.0000"))
                .allowNegative(false)
                .build());

        int threadsPerDirection = 20;
        int totalThreads = threadsPerDirection * 2;
        BigDecimal transferAmount = new BigDecimal("100000.0000");

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalThreads);

        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger deadlocksOrErrors = new AtomicInteger(0);

        // Direction 1: 20 threads transferring A -> B
        for (int i = 0; i < threadsPerDirection; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    RecordTransactionRequest req = RecordTransactionRequest.builder()
                            .description("Cross Transfer A to B #" + index)
                            .referenceId("CROSS-AB-" + UUID.randomUUID())
                            .postingLegs(List.of(
                                    PostingLegRequest.builder()
                                            .accountId(accA.getId())
                                            .amount(transferAmount.negate())
                                            .build(),
                                    PostingLegRequest.builder()
                                            .accountId(accB.getId())
                                            .amount(transferAmount)
                                            .build()
                            ))
                            .build();

                    ledgerService.recordTransaction(req);
                    completedCount.incrementAndGet();
                } catch (Exception e) {
                    deadlocksOrErrors.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Direction 2: 20 threads transferring B -> A (Concurrent reverse direction)
        for (int i = 0; i < threadsPerDirection; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    RecordTransactionRequest req = RecordTransactionRequest.builder()
                            .description("Cross Transfer B to A #" + index)
                            .referenceId("CROSS-BA-" + UUID.randomUUID())
                            .postingLegs(List.of(
                                    PostingLegRequest.builder()
                                            .accountId(accB.getId())
                                            .amount(transferAmount.negate())
                                            .build(),
                                    PostingLegRequest.builder()
                                            .accountId(accA.getId())
                                            .amount(transferAmount)
                                            .build()
                            ))
                            .build();

                    ledgerService.recordTransaction(req);
                    completedCount.incrementAndGet();
                } catch (Exception e) {
                    deadlocksOrErrors.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown(); // Unleash both directions concurrently
        boolean finished = endGate.await(45, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("All 40 cross-transfer threads should finish without deadlock").isTrue();
        assertThat(deadlocksOrErrors.get()).as("Zero deadlocks should occur thanks to deterministic ORDER BY a.id ASC").isEqualTo(0);
        assertThat(completedCount.get()).as("All 40 cross transactions must succeed").isEqualTo(40);

        // Since 20 transfers A->B and 20 transfers B->A of equal amount (100,000 VND),
        // net change for both accounts should be exactly 0
        AccountResponse finalAccA = ledgerService.getAccount(accA.getId());
        AccountResponse finalAccB = ledgerService.getAccount(accB.getId());

        assertThat(finalAccA.getCachedBalance()).isEqualByComparingTo(new BigDecimal("10000000.0000"));
        assertThat(finalAccB.getCachedBalance()).isEqualByComparingTo(new BigDecimal("10000000.0000"));
    }
}
