package vn.finledger.splitbill.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.finledger.ledger.domain.Account;
import vn.finledger.ledger.domain.AccountType;
import vn.finledger.ledger.dto.AccountResponse;
import vn.finledger.ledger.dto.CreateAccountRequest;
import vn.finledger.ledger.dto.JournalEntryResponse;
import vn.finledger.ledger.dto.PostingLegRequest;
import vn.finledger.ledger.dto.RecordTransactionRequest;
import vn.finledger.ledger.repository.AccountRepository;
import vn.finledger.ledger.service.LedgerService;
import vn.finledger.splitbill.algorithm.DebtSimplificationEngine;
import vn.finledger.splitbill.calculator.PennyExactSplitCalculator;
import vn.finledger.splitbill.calculator.SplitShareInput;
import vn.finledger.splitbill.domain.*;
import vn.finledger.splitbill.dto.DebtSettlementDto;
import vn.finledger.splitbill.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GroupSplitServiceTest {

    @Mock
    private ExpenseGroupRepository expenseGroupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private GroupExpenseRepository groupExpenseRepository;

    @Mock
    private GroupExpenseShareRepository groupExpenseShareRepository;

    @Mock
    private DebtSettlementRepository debtSettlementRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private AccountRepository accountRepository;

    @Spy
    private PennyExactSplitCalculator splitCalculator = new PennyExactSplitCalculator();

    @Spy
    private DebtSimplificationEngine debtSimplificationEngine = new DebtSimplificationEngine();

    @InjectMocks
    private GroupSplitService groupSplitService;

    private ExpenseGroup group;
    private GroupMember hieu;
    private GroupMember minh;
    private GroupMember tung;
    private GroupMember hai;
    private UUID hieuAccountId;
    private UUID minhAccountId;
    private UUID tungAccountId;
    private UUID haiAccountId;

    @BeforeEach
    void setUp() {
        group = ExpenseGroup.builder()
                .id(UUID.randomUUID())
                .name("Phong 404")
                .currency("VND")
                .shareToken("sharetoken123")
                .simplifyDebts(true)
                .build();

        hieuAccountId = UUID.randomUUID();
        minhAccountId = UUID.randomUUID();
        tungAccountId = UUID.randomUUID();
        haiAccountId = UUID.randomUUID();

        hieu = GroupMember.builder()
                .id(UUID.randomUUID())
                .group(group)
                .displayName("Hieu")
                .bankCode("VCB")
                .bankAccountNumber("0011002233")
                .memberLedgerAccountId(hieuAccountId)
                .build();

        minh = GroupMember.builder()
                .id(UUID.randomUUID())
                .group(group)
                .displayName("Minh")
                .bankCode("TPB")
                .bankAccountNumber("0987654321")
                .memberLedgerAccountId(minhAccountId)
                .build();

        tung = GroupMember.builder()
                .id(UUID.randomUUID())
                .group(group)
                .displayName("Tung")
                .bankCode("MB")
                .bankAccountNumber("1122334455")
                .memberLedgerAccountId(tungAccountId)
                .build();

        hai = GroupMember.builder()
                .id(UUID.randomUUID())
                .group(group)
                .displayName("Hai")
                .bankCode("TCB")
                .bankAccountNumber("9988776655")
                .memberLedgerAccountId(haiAccountId)
                .build();
    }

    @Test
    @DisplayName("Record expense creates double-entry transaction strictly balancing to 0.0000")
    void testRecordGroupExpenseBalancingInvariant() {
        when(expenseGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
        when(groupMemberRepository.findById(hieu.getId())).thenReturn(Optional.of(hieu));
        when(groupMemberRepository.findByGroupId(group.getId())).thenReturn(List.of(hieu, minh, tung, hai));

        UUID generatedJournalId = UUID.randomUUID();
        when(ledgerService.recordTransaction(any(RecordTransactionRequest.class)))
                .thenAnswer(invocation -> {
                    RecordTransactionRequest req = invocation.getArgument(0);
                    BigDecimal sum = req.getPostingLegs().stream()
                            .map(PostingLegRequest::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(4, RoundingMode.HALF_EVEN);

                    assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO.setScale(4));

                    return JournalEntryResponse.builder()
                            .id(generatedJournalId)
                            .transactionDate(req.getTransactionDate())
                            .description(req.getDescription())
                            .build();
                });

        when(groupExpenseRepository.save(any(GroupExpense.class))).thenAnswer(i -> {
            GroupExpense ge = i.getArgument(0);
            ge.setId(UUID.randomUUID());
            return ge;
        });

        List<SplitShareInput> inputs = List.of(
                new SplitShareInput(hieu.getId()),
                new SplitShareInput(minh.getId()),
                new SplitShareInput(tung.getId()),
                new SplitShareInput(hai.getId())
        );

        GroupExpense expense = groupSplitService.recordGroupExpense(
                group.getId(), hieu.getId(), "Tien nha",
                new BigDecimal("4000000"), SplitStrategy.EQUAL, inputs);

        assertThat(expense).isNotNull();
        assertThat(expense.getJournalEntryId()).isEqualTo(generatedJournalId);

        ArgumentCaptor<RecordTransactionRequest> captor = ArgumentCaptor.forClass(RecordTransactionRequest.class);
        verify(ledgerService).recordTransaction(captor.capture());

        RecordTransactionRequest req = captor.getValue();
        // 1 payer leg (-4.000.000) + 4 participant legs (+1.000.000 each) = 5 legs
        assertThat(req.getPostingLegs()).hasSize(5);

        PostingLegRequest payerLeg = req.getPostingLegs().get(0);
        assertThat(payerLeg.getAccountId()).isEqualTo(hieuAccountId);
        assertThat(payerLeg.getAmount()).isEqualByComparingTo(new BigDecimal("-4000000.0000"));

        BigDecimal participantsSum = req.getPostingLegs().subList(1, 5).stream()
                .map(PostingLegRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(participantsSum).isEqualByComparingTo(new BigDecimal("4000000.0000"));
    }

    @Test
    @DisplayName("Generate settlement plan creates minimal transfers with VietQR URLs")
    void testGenerateSettlementPlan() {
        when(expenseGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupId(group.getId())).thenReturn(List.of(hieu, minh, tung, hai));

        // Ledger balances:
        // Hieu: -3.000.000 (Creditor)
        // Minh: +1.000.000 (Debtor)
        // Tung: +1.000.000 (Debtor)
        // Hai:  +1.000.000 (Debtor)
        when(accountRepository.findById(hieuAccountId)).thenReturn(Optional.of(
                Account.builder().id(hieuAccountId).cachedBalance(new BigDecimal("-3000000.0000")).build()));
        when(accountRepository.findById(minhAccountId)).thenReturn(Optional.of(
                Account.builder().id(minhAccountId).cachedBalance(new BigDecimal("1000000.0000")).build()));
        when(accountRepository.findById(tungAccountId)).thenReturn(Optional.of(
                Account.builder().id(tungAccountId).cachedBalance(new BigDecimal("1000000.0000")).build()));
        when(accountRepository.findById(haiAccountId)).thenReturn(Optional.of(
                Account.builder().id(haiAccountId).cachedBalance(new BigDecimal("1000000.0000")).build()));

        when(debtSettlementRepository.save(any(DebtSettlement.class))).thenAnswer(i -> {
            DebtSettlement ds = i.getArgument(0);
            ds.setId(UUID.randomUUID());
            return ds;
        });

        List<DebtSettlementDto> plan = groupSplitService.generateSettlementPlan(group.getId());

        // 3 debtors pay Hieu directly -> 3 transfers
        assertThat(plan).hasSize(3);

        for (DebtSettlementDto dto : plan) {
            assertThat(dto.getToMemberId()).isEqualTo(hieu.getId());
            assertThat(dto.getAmount()).isEqualByComparingTo(new BigDecimal("1000000.0000"));
            assertThat(dto.getSettlementCode()).startsWith("FLV");
            assertThat(dto.getVietQrUrl()).contains("https://img.vietqr.io/image/VCB-0011002233-compact2.png?amount=1000000&addInfo=" + dto.getSettlementCode());
        }
    }

    @Test
    @DisplayName("Settle debt posts balancing double-entry transfer and marks status COMPLETED")
    void testSettleDebt() {
        UUID settlementId = UUID.randomUUID();
        String code = "FLV999888";
        DebtSettlement settlement = DebtSettlement.builder()
                .id(settlementId)
                .group(group)
                .fromMember(hai)
                .toMember(hieu)
                .amount(new BigDecimal("1000000.0000"))
                .settlementCode(code)
                .status(SettlementStatus.PENDING)
                .build();

        when(debtSettlementRepository.findById(settlementId)).thenReturn(Optional.of(settlement));

        UUID journalId = UUID.randomUUID();
        when(ledgerService.recordTransaction(any(RecordTransactionRequest.class)))
                .thenReturn(JournalEntryResponse.builder().id(journalId).build());

        JournalEntryResponse response = groupSplitService.settleDebt(settlementId);

        assertThat(response.getId()).isEqualTo(journalId);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(settlement.getCompletedAt()).isNotNull();
        assertThat(settlement.getJournalEntryId()).isEqualTo(journalId);

        ArgumentCaptor<RecordTransactionRequest> captor = ArgumentCaptor.forClass(RecordTransactionRequest.class);
        verify(ledgerService).recordTransaction(captor.capture());

        RecordTransactionRequest req = captor.getValue();
        assertThat(req.getReferenceId()).isEqualTo("SETTLE:" + code);
        assertThat(req.getPostingLegs()).hasSize(2);

        // Hai (fromMember) is credited -1000000
        PostingLegRequest haiLeg = req.getPostingLegs().get(0);
        assertThat(haiLeg.getAccountId()).isEqualTo(haiAccountId);
        assertThat(haiLeg.getAmount()).isEqualByComparingTo(new BigDecimal("-1000000.0000"));

        // Hieu (toMember) is debited +1000000
        PostingLegRequest hieuLeg = req.getPostingLegs().get(1);
        assertThat(hieuLeg.getAccountId()).isEqualTo(hieuAccountId);
        assertThat(hieuLeg.getAmount()).isEqualByComparingTo(new BigDecimal("1000000.0000"));
    }
}
