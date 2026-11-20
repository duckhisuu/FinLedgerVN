package vn.finledger.ingestion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import vn.finledger.auth.security.CurrentUserProvider;
import vn.finledger.ingestion.controller.VcbMomoIngestionController;
import vn.finledger.ingestion.dedup.InternalTransferReconciler;
import vn.finledger.ingestion.dedup.MultiSourceFuzzyDeduplicator;
import vn.finledger.ingestion.dedup.MultiSourceIngestionResult;
import vn.finledger.ingestion.dedup.MultiSourceIngestionService;
import vn.finledger.ingestion.dedup.MultiSourceTransactionalProcessor;
import vn.finledger.ingestion.dedup.UnauthorizedBridgeException;
import vn.finledger.ingestion.domain.BankAccountMapping;
import vn.finledger.ingestion.domain.IngestionFingerprint;
import vn.finledger.ingestion.dto.ConnectedSourceTelemetryDto;
import vn.finledger.ingestion.dto.MobileBridgeAlertRequest;
import vn.finledger.ingestion.parsers.*;
import vn.finledger.ingestion.repository.BankAccountMappingRepository;
import vn.finledger.ingestion.repository.CategorizationRuleRepository;
import vn.finledger.ingestion.repository.IngestionFingerprintRepository;
import vn.finledger.ingestion.service.CategorizationResult;
import vn.finledger.ingestion.service.ConnectedSourceService;
import vn.finledger.ingestion.service.LocalTrainableCategorizer;
import vn.finledger.ledger.domain.Account;
import vn.finledger.ledger.domain.AccountType;
import vn.finledger.ledger.dto.JournalEntryResponse;
import vn.finledger.ledger.dto.PostingLegRequest;
import vn.finledger.ledger.dto.RecordTransactionRequest;
import vn.finledger.ledger.repository.AccountRepository;
import vn.finledger.ledger.service.LedgerService;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VcbMomoParsersAndDeduplicationUnitTest {

    private final VietcombankParserPlugin vcbPlugin = new VietcombankParserPlugin();
    private final MoMoWalletParserPlugin momoPlugin = new MoMoWalletParserPlugin();

    @Test
    @DisplayName("VCB Parser extracts account, signed delta, running balance, Ref code, and narrative (both OTT accented & SMS unaccented)")
    void testVietcombankParser() {
        String vcbOtt = "Số dư TK VCB 0011001988888 thay đổi -55,000 VND lúc 13-11-2026 14:20:10. Số dư 12,450,000 VND. Ref MBVCB.73829102.091234.NGUYEN VAN A chuyen tien an trua.CT tu 0011...";

        assertThat(vcbPlugin.supports("com.VCB", null, vcbOtt, IngestionChannel.MOBILE_NOTIFICATION)).isTrue();
        assertThat(momoPlugin.supports("com.VCB", null, vcbOtt, IngestionChannel.MOBILE_NOTIFICATION)).isFalse();

        ParsedBalanceEvent ottEvent = vcbPlugin.parse("com.VCB", null, vcbOtt, IngestionChannel.MOBILE_NOTIFICATION, Instant.now())
                .orElseThrow();
        assertThat(ottEvent.providerCode()).isEqualTo("VCB");
        assertThat(ottEvent.accountIdentifier()).isEqualTo("0011001988888");
        assertThat(ottEvent.transferDirection()).isEqualTo(TransferDirection.OUT);
        assertThat(ottEvent.amount()).isEqualByComparingTo("55000.0000");
        assertThat(ottEvent.runningBalanceAfterTx()).isEqualByComparingTo("12450000.0000");
        assertThat(ottEvent.extractedReferenceCode()).isEqualTo("MBVCB.73829102");
        assertThat(ottEvent.rawDescription()).contains("NGUYEN VAN A chuyen tien an trua");
        assertThat(ottEvent.linkedBankTransfer()).isFalse();

        String vcbSmsMoMo = "SD TK 0011001988888 -500,000VND luc 13-11-2026 14:25:00. SD 11,950,000VND. Ref ECOM261113.MOMO.Nap tien vi MoMo";
        ParsedBalanceEvent momoTopupEvent = vcbPlugin.parse("VCB", null, vcbSmsMoMo, IngestionChannel.MOBILE_NOTIFICATION, Instant.now())
                .orElseThrow();
        assertThat(momoTopupEvent.transferDirection()).isEqualTo(TransferDirection.OUT);
        assertThat(momoTopupEvent.amount()).isEqualByComparingTo("500000.0000");
        assertThat(momoTopupEvent.runningBalanceAfterTx()).isEqualByComparingTo("11950000.0000");
        assertThat(momoTopupEvent.extractedReferenceCode()).isEqualTo("ECOM261113");
        assertThat(momoTopupEvent.linkedBankTransfer()).isTrue();
        assertThat(momoTopupEvent.counterpartyProviderCode()).isEqualTo("MOMO");
    }

    @Test
    @DisplayName("MoMo Parser extracts Merchant payment, P2P receipt, and linked Vietcombank top-up without colliding with VCB plugin")
    void testMoMoParser() {
        // Sample 1: Service payment
        String momoMerchant = "Bạn vừa thanh toán 45.000đ cho CIRCLE K VIETNAM. Mã GD: 68291048291. Số dư ví: 320.000đ. Nội dung: Mua nuoc ngot";
        assertThat(momoPlugin.supports("MoMo", null, momoMerchant, IngestionChannel.MOBILE_NOTIFICATION)).isTrue();
        ParsedBalanceEvent e1 = momoPlugin.parse("MoMo", null, momoMerchant, IngestionChannel.MOBILE_NOTIFICATION, Instant.now())
                .orElseThrow();
        assertThat(e1.providerCode()).isEqualTo("MOMO");
        assertThat(e1.transferDirection()).isEqualTo(TransferDirection.OUT);
        assertThat(e1.amount()).isEqualByComparingTo("45000.0000");
        assertThat(e1.runningBalanceAfterTx()).isEqualByComparingTo("320000.0000");
        assertThat(e1.extractedReferenceCode()).isEqualTo("68291048291");
        assertThat(e1.counterpartyHint()).isEqualTo("CIRCLE K VIETNAM");
        assertThat(e1.linkedBankTransfer()).isFalse();

        // Sample 2: P2P incoming
        String momoP2p = "Bạn vừa nhận 150.000đ từ TRẦN VĂN B (0987***123). Lời nhắn: trả tiền bún chả hôm qua. Mã GD: 68291099999";
        ParsedBalanceEvent e2 = momoPlugin.parse("MoMo", null, momoP2p, IngestionChannel.MOBILE_NOTIFICATION, Instant.now())
                .orElseThrow();
        assertThat(e2.transferDirection()).isEqualTo(TransferDirection.IN);
        assertThat(e2.amount()).isEqualByComparingTo("150000.0000");
        assertThat(e2.extractedReferenceCode()).isEqualTo("68291099999");
        assertThat(e2.linkedBankTransfer()).isFalse();

        // Sample 3: Linked Vietcombank topup
        String momoTopup = "Nạp tiền thành công 500.000đ vào Ví MoMo từ nguồn tiền Vietcombank. Mã GD: 68291055555. Số dư: 820.000đ";
        assertThat(vcbPlugin.supports(null, null, momoTopup, IngestionChannel.MOBILE_NOTIFICATION)).isFalse();
        assertThat(momoPlugin.supports(null, null, momoTopup, IngestionChannel.MOBILE_NOTIFICATION)).isTrue();
        ParsedBalanceEvent e3 = momoPlugin.parse(null, null, momoTopup, IngestionChannel.MOBILE_NOTIFICATION, Instant.now())
                .orElseThrow();
        assertThat(e3.transferDirection()).isEqualTo(TransferDirection.IN);
        assertThat(e3.amount()).isEqualByComparingTo("500000.0000");
        assertThat(e3.runningBalanceAfterTx()).isEqualByComparingTo("820000.0000");
        assertThat(e3.extractedReferenceCode()).isEqualTo("68291055555");
        assertThat(e3.linkedBankTransfer()).isTrue();
        assertThat(e3.counterpartyProviderCode()).isEqualTo("VCB");
    }

    @Test
    @DisplayName("MultiSourceFuzzyDeduplicator merges Ref-less Email into existing Mobile Push within 5-minute window")
    @SuppressWarnings("unchecked")
    void testFuzzyWindowMergesRefLessEmail() {
        IngestionFingerprintRepository fpRepo = mock(IngestionFingerprintRepository.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);

        MultiSourceFuzzyDeduplicator deduplicator = new MultiSourceFuzzyDeduplicator(fpRepo, redis);

        UUID accountId = UUID.randomUUID();
        UUID jeId = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-11-13T07:20:10Z");
        long bucketT0 = Math.floorDiv(t0.getEpochSecond(), 300L);

        IngestionFingerprint existingFp = IngestionFingerprint.builder()
                .id(UUID.randomUUID())
                .sourceAccountId(accountId)
                .transferDirection("OUT")
                .exactAmount(new BigDecimal("55000.0000"))
                .timeBucket5m(bucketT0)
                .normalizedRefCode("MBVCB.73829102")
                .fuzzySignature("sig-abc")
                .journalEntryId(jeId)
                .sourcesMerged("MOBILE_PUSH")
                .build();

        when(fpRepo.findRecentFuzzyMatches(any(), eq("OUT"), eq(new BigDecimal("55000.0000")), any()))
                .thenReturn(List.of(existingFp));
        when(fpRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Incoming email 2 minutes later WITHOUT Reference Code
        ParsedBalanceEvent emailEvent = new ParsedBalanceEvent(
                "VCB",
                IngestionChannel.EMAIL,
                "0011001988888",
                TransferDirection.OUT,
                new BigDecimal("55000.0000"),
                new BigDecimal("12450000.0000"),
                "NGUYEN VAN A chuyen tien an trua",
                null,
                null,
                t0.plusSeconds(120)
        );

        MultiSourceFuzzyDeduplicator.DedupCheckResult result =
                deduplicator.checkAndMergeIfDuplicate(accountId, emailEvent);

        assertThat(result.duplicate()).isTrue();
        assertThat(result.journalEntryId()).isEqualTo(jeId);
        assertThat(result.sourcesMerged()).isEqualTo("MOBILE_PUSH,EMAIL");
    }

    @Test
    @DisplayName("MultiSourceIngestionService rejects missing or invalid X-Bridge-Token with UnauthorizedBridgeException")
    void testUnauthorizedBridgeTokenRejected() {
        BankAccountMappingRepository mappingRepo = mock(BankAccountMappingRepository.class);
        MultiSourceTransactionalProcessor processor = mock(MultiSourceTransactionalProcessor.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(mappingRepo.findByWebhookSecretToken("wrong_token")).thenReturn(Collections.emptyList());

        MultiSourceIngestionService service = new MultiSourceIngestionService(
                List.of(vcbPlugin, momoPlugin),
                mappingRepo,
                processor,
                redis
        );

        assertThatThrownBy(() -> service.ingestMobileBridgeAlert(null, "VCB", "test", null, Instant.now()))
                .isInstanceOf(UnauthorizedBridgeException.class);

        assertThatThrownBy(() -> service.ingestMobileBridgeAlert("wrong_token", "VCB", "test", null, Instant.now()))
                .isInstanceOf(UnauthorizedBridgeException.class);

        verifyNoInteractions(processor);
    }

    @Test
    @DisplayName("InternalTransferReconciler creates 1 balanced TRANSFER JournalEntry on VCB topup and links MoMo counterpart cleanly")
    @SuppressWarnings("unchecked")
    void testInternalTransferReconcilerCreatesThenLinks() {
        BankAccountMappingRepository mappingRepo = mock(BankAccountMappingRepository.class);
        MultiSourceFuzzyDeduplicator deduplicator = mock(MultiSourceFuzzyDeduplicator.class);
        LedgerService ledgerService = mock(LedgerService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        InternalTransferReconciler reconciler = new InternalTransferReconciler(
                mappingRepo, deduplicator, ledgerService, redis
        );

        UUID userId = UUID.randomUUID();
        UUID vcbLedgerId = UUID.randomUUID();
        UUID momoLedgerId = UUID.randomUUID();
        UUID jeId = UUID.randomUUID();

        BankAccountMapping vcbMapping = BankAccountMapping.builder()
                .userId(userId)
                .providerCode("VCB")
                .accountIdentifier("0011001988888")
                .bankAccountNumber("0011001988888")
                .bankName("VCB")
                .ledgerAccountId(vcbLedgerId)
                .webhookSecretToken("tok")
                .build();

        BankAccountMapping momoMapping = BankAccountMapping.builder()
                .userId(userId)
                .providerCode("MOMO")
                .accountIdentifier("0912345678")
                .bankAccountNumber("0912345678")
                .bankName("MOMO")
                .ledgerAccountId(momoLedgerId)
                .webhookSecretToken("tok")
                .build();

        when(mappingRepo.findFirstByUserIdAndProviderCodeOrderByCreatedAtAsc(userId, "MOMO"))
                .thenReturn(Optional.of(momoMapping));
        when(mappingRepo.findFirstByUserIdAndProviderCodeOrderByCreatedAtAsc(userId, "VCB"))
                .thenReturn(Optional.of(vcbMapping));
        when(ledgerService.recordTransaction(any(RecordTransactionRequest.class)))
                .thenReturn(JournalEntryResponse.builder().id(jeId).build());
        when(deduplicator.recordFingerprint(any(), any(), eq(jeId), any()))
                .thenReturn(IngestionFingerprint.builder().id(UUID.randomUUID()).sourcesMerged("MOBILE_PUSH").build());

        // Step 1: VCB -500,000 ARRIVES FIRST (nothing pending yet)
        String vcbWaitKey = InternalTransferReconciler.buildPendingKey(userId.toString(), "VCB", "OUT", new BigDecimal("500000"));
        when(valueOps.get(vcbWaitKey)).thenReturn(null);

        ParsedBalanceEvent vcbEvent = vcbPlugin.parse(
                "VCB", null,
                "SD TK 0011001988888 -500,000VND luc 13-11-2026 14:25:00. SD 11,950,000VND. Ref ECOM261113.MOMO.Nap tien vi MoMo",
                IngestionChannel.MOBILE_NOTIFICATION,
                Instant.now()
        ).orElseThrow();

        InternalTransferReconciler.InternalReconciliationResult r1 = reconciler.reconcileIfInternalTransfer(vcbMapping, vcbEvent);
        assertThat(r1.handled()).isTrue();
        assertThat(r1.status()).isEqualTo("INTERNAL_TRANSFER_CREATED");
        assertThat(r1.journalEntryId()).isEqualTo(jeId);

        String momoWaitKey = InternalTransferReconciler.buildPendingKey(userId.toString(), "MOMO", "IN", new BigDecimal("500000"));
        verify(valueOps).set(eq(momoWaitKey), eq(jeId.toString()), any());

        // Step 2: MoMo +500,000 ARRIVES SECOND -> finds pending key on Redis, links without calling recordTransaction again
        when(valueOps.get(momoWaitKey)).thenReturn(jeId.toString());
        ParsedBalanceEvent momoEvent = momoPlugin.parse(
                "MoMo", null,
                "Nạp tiền thành công 500.000đ vào Ví MoMo từ nguồn tiền Vietcombank. Mã GD: 68291055555. Số dư: 820.000đ",
                IngestionChannel.MOBILE_NOTIFICATION,
                Instant.now()
        ).orElseThrow();

        InternalTransferReconciler.InternalReconciliationResult r2 = reconciler.reconcileIfInternalTransfer(momoMapping, momoEvent);
        assertThat(r2.handled()).isTrue();
        assertThat(r2.status()).isEqualTo("INTERNAL_TRANSFER_LINKED");
        assertThat(r2.journalEntryId()).isEqualTo(jeId);

        verify(ledgerService, times(1)).recordTransaction(any(RecordTransactionRequest.class));
        verify(redis).delete(momoWaitKey);
    }

    @Test
    @DisplayName("InternalTransferReconciler handles reverse arrival (MoMo -> VCB), checks legs sum to 0.0000 and EXPENSE/REVENUE untouched")
    @SuppressWarnings("unchecked")
    void testInternalTransferReverseOrderAndDoubleEntryZeroSum() {
        BankAccountMappingRepository mappingRepo = mock(BankAccountMappingRepository.class);
        MultiSourceFuzzyDeduplicator deduplicator = mock(MultiSourceFuzzyDeduplicator.class);
        LedgerService ledgerService = mock(LedgerService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        InternalTransferReconciler reconciler = new InternalTransferReconciler(
                mappingRepo, deduplicator, ledgerService, redis
        );

        UUID userId = UUID.randomUUID();
        UUID vcbLedgerId = UUID.randomUUID();
        UUID momoLedgerId = UUID.randomUUID();
        UUID jeId = UUID.randomUUID();

        BankAccountMapping vcbMapping = BankAccountMapping.builder()
                .userId(userId).providerCode("VCB").accountIdentifier("0011001988888")
                .bankAccountNumber("0011001988888").bankName("VCB").ledgerAccountId(vcbLedgerId)
                .webhookSecretToken("tok").build();
        BankAccountMapping momoMapping = BankAccountMapping.builder()
                .userId(userId).providerCode("MOMO").accountIdentifier("0912345678")
                .bankAccountNumber("0912345678").bankName("MOMO").ledgerAccountId(momoLedgerId)
                .webhookSecretToken("tok").build();

        when(mappingRepo.findFirstByUserIdAndProviderCodeOrderByCreatedAtAsc(userId, "VCB"))
                .thenReturn(Optional.of(vcbMapping));
        when(mappingRepo.findFirstByUserIdAndProviderCodeOrderByCreatedAtAsc(userId, "MOMO"))
                .thenReturn(Optional.of(momoMapping));
        when(ledgerService.recordTransaction(any(RecordTransactionRequest.class)))
                .thenReturn(JournalEntryResponse.builder().id(jeId).build());
        when(deduplicator.recordFingerprint(any(), any(), eq(jeId), any()))
                .thenReturn(IngestionFingerprint.builder().id(UUID.randomUUID()).sourcesMerged("MOBILE_PUSH").build());

        // Step 1: MoMo +500,000 arrives FIRST
        String momoSelfWait = InternalTransferReconciler.buildPendingKey(userId.toString(), "MOMO", "IN", new BigDecimal("500000"));
        when(valueOps.get(momoSelfWait)).thenReturn(null);

        ParsedBalanceEvent momoEvent = momoPlugin.parse(
                "MoMo", null,
                "Nạp tiền thành công 500.000đ vào Ví MoMo từ nguồn tiền Vietcombank. Mã GD: 68291055555. Số dư: 820.000đ",
                IngestionChannel.MOBILE_NOTIFICATION,
                Instant.now()
        ).orElseThrow();

        InternalTransferReconciler.InternalReconciliationResult r1 = reconciler.reconcileIfInternalTransfer(momoMapping, momoEvent);
        assertThat(r1.status()).isEqualTo("INTERNAL_TRANSFER_CREATED");

        ArgumentCaptor<RecordTransactionRequest> txCaptor = ArgumentCaptor.forClass(RecordTransactionRequest.class);
        verify(ledgerService, times(1)).recordTransaction(txCaptor.capture());
        RecordTransactionRequest tx = txCaptor.getValue();
        assertThat(tx.getEntryType()).isEqualTo("TRANSFER");
        assertThat(tx.getPostingLegs()).hasSize(2);
        BigDecimal sum = tx.getPostingLegs().stream()
                .map(PostingLegRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("0.0000");
        assertThat(tx.getPostingLegs().stream().map(PostingLegRequest::getAccountId))
                .containsExactlyInAnyOrder(vcbLedgerId, momoLedgerId);

        // Step 2: VCB -500,000 arrives SECOND -> links into same jeId
        String vcbWaitKey = InternalTransferReconciler.buildPendingKey(userId.toString(), "VCB", "OUT", new BigDecimal("500000"));
        when(valueOps.get(vcbWaitKey)).thenReturn(jeId.toString());

        ParsedBalanceEvent vcbEvent = vcbPlugin.parse(
                "VCB", null,
                "SD TK 0011001988888 -500,000VND luc 13-11-2026 14:25:00. SD 11,950,000VND. Ref ECOM261113.MOMO.Nap tien vi MoMo",
                IngestionChannel.MOBILE_NOTIFICATION,
                Instant.now()
        ).orElseThrow();

        InternalTransferReconciler.InternalReconciliationResult r2 = reconciler.reconcileIfInternalTransfer(vcbMapping, vcbEvent);
        assertThat(r2.status()).isEqualTo("INTERNAL_TRANSFER_LINKED");
        assertThat(r2.journalEntryId()).isEqualTo(jeId);
        verify(ledgerService, times(1)).recordTransaction(any());
    }

    @Test
    @DisplayName("MultiSourceFuzzyDeduplicator crosses 5m bucket boundary when Ref missing, yet keeps distinct Refs separate")
    @SuppressWarnings("unchecked")
    void testBucketBoundaryAndDistinctRefCodes() {
        IngestionFingerprintRepository fpRepo = mock(IngestionFingerprintRepository.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);

        MultiSourceFuzzyDeduplicator deduplicator = new MultiSourceFuzzyDeduplicator(fpRepo, redis);
        UUID accountId = UUID.randomUUID();
        UUID jeId = UUID.randomUUID();

        // 14:24:50Z is in bucket N, 14:26:50Z (+120s) is in bucket N+1
        Instant tBoundary = Instant.parse("2026-11-13T14:24:50Z");
        long bucketN = Math.floorDiv(tBoundary.getEpochSecond(), 300L);
        long bucketNPlus1 = Math.floorDiv(tBoundary.plusSeconds(120).getEpochSecond(), 300L);
        assertThat(bucketNPlus1).isEqualTo(bucketN + 1);

        IngestionFingerprint existingFp = IngestionFingerprint.builder()
                .id(UUID.randomUUID())
                .sourceAccountId(accountId)
                .transferDirection("OUT")
                .exactAmount(new BigDecimal("45000.0000"))
                .timeBucket5m(bucketN)
                .normalizedRefCode("68291048291")
                .fuzzySignature("sig-boundary")
                .journalEntryId(jeId)
                .sourcesMerged("MOBILE_PUSH")
                .build();

        when(fpRepo.findRecentFuzzyMatches(eq(accountId), eq("OUT"), eq(new BigDecimal("45000.0000")), any()))
                .thenReturn(List.of(existingFp));
        when(fpRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Distinct non-null Ref code (second coffee 2 minutes later) -> MUST NOT dedup!
        ParsedBalanceEvent secondCoffee = new ParsedBalanceEvent(
                "MOMO", IngestionChannel.MOBILE_NOTIFICATION, "0912345678",
                TransferDirection.OUT, new BigDecimal("45000.0000"), new BigDecimal("275000.0000"),
                "CIRCLE K VIETNAM", "68291048299", "CIRCLE K VIETNAM", tBoundary.plusSeconds(120)
        );
        MultiSourceFuzzyDeduplicator.DedupCheckResult distinctResult =
                deduplicator.checkAndMergeIfDuplicate(accountId, secondCoffee);
        assertThat(distinctResult.duplicate()).isFalse();

        // Email WITHOUT Ref code crossing bucket boundary -> MUST merge!
        ParsedBalanceEvent emailAcrossBoundary = new ParsedBalanceEvent(
                "MOMO", IngestionChannel.EMAIL, "0912345678",
                TransferDirection.OUT, new BigDecimal("45000.0000"), null,
                "CIRCLE K VIETNAM", null, "CIRCLE K VIETNAM", tBoundary.plusSeconds(120)
        );
        MultiSourceFuzzyDeduplicator.DedupCheckResult boundaryMergeResult =
                deduplicator.checkAndMergeIfDuplicate(accountId, emailAcrossBoundary);
        assertThat(boundaryMergeResult.duplicate()).isTrue();
        assertThat(boundaryMergeResult.sourcesMerged()).isEqualTo("MOBILE_PUSH,EMAIL");
    }

    @Test
    @DisplayName("LocalTrainableCategorizer classifies VCB lunch & MoMo Circle K strictly within user accounts & VcbMomoIngestionController returns 401/200")
    void testCategorizerScopingAndControllerStatusCodes() {
        CategorizationRuleRepository ruleRepo = mock(CategorizationRuleRepository.class);
        AccountRepository accRepo = mock(AccountRepository.class);
        when(ruleRepo.findActiveRulesByTransferType(any())).thenReturn(Collections.emptyList());

        UUID userId = UUID.randomUUID();
        UUID foodAccId = UUID.randomUUID();
        UUID suspenseAccId = UUID.randomUUID();
        Account foodAcc = Account.builder()
                .id(foodAccId).userId(userId).name("Ăn uống").accountType(AccountType.EXPENSE).currency("VND").build();
        when(accRepo.findByUserIdAndArchivedFalseOrderByCreatedAtAsc(userId)).thenReturn(List.of(foodAcc));

        BankAccountMapping mapping = BankAccountMapping.builder()
                .userId(userId)
                .providerCode("VCB")
                .accountIdentifier("0011001988888")
                .bankAccountNumber("0011001988888")
                .bankName("VCB")
                .defaultSuspenseExpenseAccountId(suspenseAccId)
                .defaultSuspenseRevenueAccountId(UUID.randomUUID())
                .build();

        LocalTrainableCategorizer categorizer = new LocalTrainableCategorizer(ruleRepo, accRepo);
        CategorizationResult res1 = categorizer.categorizeTransaction("NGUYEN VAN A chuyen tien an trua", TransferDirection.OUT, mapping);
        assertThat(res1.getTargetAccountId()).isEqualTo(foodAccId);

        CategorizationResult res2 = categorizer.categorizeTransaction("CIRCLE K VIETNAM - Mua nuoc ngot", TransferDirection.OUT, mapping);
        assertThat(res2.getTargetAccountId()).isEqualTo(foodAccId);

        // Controller HTTP 401 and 200 verification
        MultiSourceIngestionService ingestionService = mock(MultiSourceIngestionService.class);
        ConnectedSourceService sourceService = mock(ConnectedSourceService.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        VcbMomoIngestionController controller = new VcbMomoIngestionController(ingestionService, sourceService, currentUserProvider);

        when(ingestionService.ingestMobileBridgeAlert(isNull(), any(), any(), any(), any()))
                .thenThrow(new UnauthorizedBridgeException("Missing token"));
        ResponseEntity<?> unauth = controller.handleMobileBridge(null, new MobileBridgeAlertRequest());
        assertThat(unauth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        when(currentUserProvider.requireUserId()).thenReturn(userId);
        when(sourceService.listUserSources(userId)).thenReturn(List.of(
                ConnectedSourceTelemetryDto.builder()
                        .providerCode("VCB").reconciled(true).reconciliationDelta(BigDecimal.ZERO)
                        .build()
        ));
        ResponseEntity<List<ConnectedSourceTelemetryDto>> sourcesRes = controller.listConnectedSources();
        assertThat(sourcesRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sourcesRes.getBody()).hasSize(1);
    }
}
