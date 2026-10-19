package vn.finledger.splitbill.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.finledger.ledger.domain.Account;
import vn.finledger.ledger.domain.AccountType;
import vn.finledger.ledger.dto.CreateAccountRequest;
import vn.finledger.ledger.dto.JournalEntryResponse;
import vn.finledger.ledger.dto.PostingLegRequest;
import vn.finledger.ledger.dto.RecordTransactionRequest;
import vn.finledger.ledger.repository.AccountRepository;
import vn.finledger.ledger.service.LedgerService;
import vn.finledger.splitbill.algorithm.DebtSimplificationEngine;
import vn.finledger.splitbill.algorithm.SimplifiedTransfer;
import vn.finledger.splitbill.calculator.CalculatedShare;
import vn.finledger.splitbill.calculator.PennyExactSplitCalculator;
import vn.finledger.splitbill.calculator.SplitShareInput;
import vn.finledger.splitbill.domain.*;
import vn.finledger.splitbill.dto.DebtSettlementDto;
import vn.finledger.splitbill.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service managing group expense splitting, double-entry ledger integration,
 * debt simplification graph computation, and VietQR auto-settlement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupSplitService {

    private final ExpenseGroupRepository expenseGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupExpenseRepository groupExpenseRepository;
    private final GroupExpenseShareRepository groupExpenseShareRepository;
    private final DebtSettlementRepository debtSettlementRepository;
    private final PennyExactSplitCalculator splitCalculator;
    private final DebtSimplificationEngine debtSimplificationEngine;
    private final LedgerService ledgerService;
    private final AccountRepository accountRepository;

    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Creates a new expense group with a unique public share token.
     */
    @Transactional
    public ExpenseGroup createGroup(String name, String currency, boolean simplifyDebts) {
        String token = UUID.randomUUID().toString().replace("-", "");
        ExpenseGroup group = ExpenseGroup.builder()
                .name(name)
                .currency(currency != null ? currency : "VND")
                .shareToken(token)
                .simplifyDebts(simplifyDebts)
                .build();
        return expenseGroupRepository.save(group);
    }

    /**
     * Adds a member to an expense group and initializes a dedicated ASSET ledger account
     * with allow_negative = true to track their group debt balance on the double-entry ledger.
     */
    @Transactional
    public GroupMember addMember(
            UUID groupId,
            String displayName,
            boolean isGuest,
            String bankCode,
            String bankAccountNumber) {

        ExpenseGroup group = expenseGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Expense group not found: " + groupId));

        // Create member debt account in ledger
        String accountName = "Group [" + group.getName() + "] - " + displayName;
        var accountResponse = ledgerService.createAccount(CreateAccountRequest.builder()
                .name(accountName)
                .accountType(AccountType.ASSET)
                .currency(group.getCurrency())
                .allowNegative(true)
                .build());

        GroupMember member = GroupMember.builder()
                .group(group)
                .displayName(displayName)
                .isGuest(isGuest)
                .bankCode(bankCode)
                .bankAccountNumber(bankAccountNumber)
                .memberLedgerAccountId(accountResponse.getId())
                .build();

        return groupMemberRepository.save(member);
    }

    /**
     * Records a group expense, calculates penny-exact shares across participants,
     * and records an immutable double-entry journal entry to update member debt balances.
     */
    @Transactional
    public GroupExpense recordGroupExpense(
            UUID groupId,
            UUID paidByMemberId,
            String title,
            BigDecimal totalAmount,
            SplitStrategy strategy,
            List<SplitShareInput> splitInputs) {

        ExpenseGroup group = expenseGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Expense group not found: " + groupId));

        GroupMember payer = groupMemberRepository.findById(paidByMemberId)
                .orElseThrow(() -> new IllegalArgumentException("Paying member not found: " + paidByMemberId));

        if (!payer.getGroup().getId().equals(groupId)) {
            throw new IllegalArgumentException("Paying member does not belong to group: " + groupId);
        }

        // 1. Penny-Exact Allocation
        List<CalculatedShare> shares = splitCalculator.calculate(totalAmount, strategy, splitInputs);

        Map<UUID, GroupMember> memberMap = groupMemberRepository.findByGroupId(groupId).stream()
                .collect(Collectors.toMap(GroupMember::getId, Function.identity()));

        // 2. Build Double-Entry Posting Legs:
        // Payer CREDIT: -totalAmount (reducing debt / recognizing fronted money)
        // Participants DEBIT: +shareAmount (increasing debt obligation)
        List<PostingLegRequest> postingLegs = new ArrayList<>();
        postingLegs.add(PostingLegRequest.builder()
                .accountId(payer.getMemberLedgerAccountId())
                .amount(totalAmount.setScale(4, RoundingMode.HALF_EVEN).negate())
                .build());

        for (CalculatedShare share : shares) {
            GroupMember member = memberMap.get(share.memberId());
            if (member == null) {
                throw new IllegalArgumentException("Participant " + share.memberId() + " does not belong to group");
            }
            postingLegs.add(PostingLegRequest.builder()
                    .accountId(member.getMemberLedgerAccountId())
                    .amount(share.shareAmount().setScale(4, RoundingMode.HALF_EVEN))
                    .build());
        }

        // 3. Persist into Ledger Engine (Strict invariant: SUM = 0.0000)
        String refId = "EXPENSE:" + UUID.randomUUID();
        JournalEntryResponse journalEntry = ledgerService.recordTransaction(RecordTransactionRequest.builder()
                .transactionDate(Instant.now())
                .description("Group Expense: " + title + " (" + group.getName() + ")")
                .referenceId(refId)
                .postingLegs(postingLegs)
                .build());

        // 4. Save Expense and Shares
        GroupExpense expense = GroupExpense.builder()
                .group(group)
                .paidByMember(payer)
                .title(title)
                .totalAmount(totalAmount.setScale(4, RoundingMode.HALF_EVEN))
                .splitStrategy(strategy)
                .journalEntryId(journalEntry.getId())
                .build();
        expense = groupExpenseRepository.save(expense);

        for (CalculatedShare share : shares) {
            GroupMember member = memberMap.get(share.memberId());
            GroupExpenseShare expenseShare = GroupExpenseShare.builder()
                    .groupExpense(expense)
                    .member(member)
                    .shareAmount(share.shareAmount())
                    .rawWeight(share.rawWeight())
                    .build();
            groupExpenseShareRepository.save(expenseShare);
        }

        return expense;
    }

    /**
     * Generates an optimal debt settlement plan using Min-Cash-Flow Debt Simplification,
     * assigns unique VietQR settlement codes, and constructs quicklink QR URLs.
     */
    @Transactional
    public List<DebtSettlementDto> generateSettlementPlan(UUID groupId) {
        ExpenseGroup group = expenseGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Expense group not found: " + groupId));

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        if (members.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, GroupMember> memberMap = members.stream()
                .collect(Collectors.toMap(GroupMember::getId, Function.identity()));

        // Lookup current debt balances from ledger accounts
        // cached_balance > 0 means DEBTOR (owes group)
        // cached_balance < 0 means CREDITOR (is owed by group)
        // DebtSimplificationEngine expects netBalance > 0 for CREDITOR, netBalance < 0 for DEBTOR
        Map<UUID, BigDecimal> netBalances = new HashMap<>();
        for (GroupMember member : members) {
            Account account = accountRepository.findById(member.getMemberLedgerAccountId())
                    .orElseThrow(() -> new IllegalStateException("Account not found for member: " + member.getId()));
            BigDecimal ledgerBalance = account.getCachedBalance();
            netBalances.put(member.getId(), ledgerBalance.negate());
        }

        List<SimplifiedTransfer> simplified = debtSimplificationEngine.simplifyFromNetBalances(netBalances);

        List<DebtSettlementDto> settlementDtos = new ArrayList<>();

        for (SimplifiedTransfer transfer : simplified) {
            GroupMember fromMember = memberMap.get(transfer.fromMemberId());
            GroupMember toMember = memberMap.get(transfer.toMemberId());

            String code = generateSettlementCode();

            DebtSettlement settlement = DebtSettlement.builder()
                    .group(group)
                    .fromMember(fromMember)
                    .toMember(toMember)
                    .amount(transfer.amount())
                    .settlementCode(code)
                    .status(SettlementStatus.PENDING)
                    .build();

            settlement = debtSettlementRepository.save(settlement);

            String vietQrUrl = buildVietQrUrl(toMember, transfer.amount(), code);

            settlementDtos.add(DebtSettlementDto.builder()
                    .id(settlement.getId())
                    .fromMemberId(fromMember.getId())
                    .fromMemberName(fromMember.getDisplayName())
                    .toMemberId(toMember.getId())
                    .toMemberName(toMember.getDisplayName())
                    .toBankCode(toMember.getBankCode())
                    .toBankAccountNumber(toMember.getBankAccountNumber())
                    .amount(transfer.amount())
                    .settlementCode(code)
                    .status(settlement.getStatus().name())
                    .vietQrUrl(vietQrUrl)
                    .build());
        }

        return settlementDtos;
    }

    /**
     * Settles a pending debt settlement record by posting a balancing double-entry transfer
     * between debtor and creditor ledger accounts and marking status as COMPLETED.
     */
    @Transactional
    public JournalEntryResponse settleDebt(UUID settlementId) {
        DebtSettlement settlement = debtSettlementRepository.findById(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("Debt settlement not found: " + settlementId));

        if (settlement.getStatus() == SettlementStatus.COMPLETED) {
            log.info("Debt settlement {} is already COMPLETED", settlementId);
            return ledgerService.getJournalEntry(settlement.getJournalEntryId());
        }

        GroupMember fromMember = settlement.getFromMember();
        GroupMember toMember = settlement.getToMember();
        BigDecimal amount = settlement.getAmount().setScale(4, RoundingMode.HALF_EVEN);

        // Double-entry balancing legs:
        // FromMember (Debtor) CREDIT: -amount (erases positive debt)
        // ToMember (Creditor) DEBIT: +amount (erases negative receivable)
        // SUM = (-amount) + (+amount) = 0.0000
        RecordTransactionRequest request = RecordTransactionRequest.builder()
                .transactionDate(Instant.now())
                .description("Settlement: " + fromMember.getDisplayName() + " -> "
                        + toMember.getDisplayName() + " [" + settlement.getSettlementCode() + "]")
                .referenceId("SETTLE:" + settlement.getSettlementCode())
                .postingLegs(List.of(
                        PostingLegRequest.builder()
                                .accountId(fromMember.getMemberLedgerAccountId())
                                .amount(amount.negate())
                                .build(),
                        PostingLegRequest.builder()
                                .accountId(toMember.getMemberLedgerAccountId())
                                .amount(amount)
                                .build()
                ))
                .build();

        JournalEntryResponse entryResponse = ledgerService.recordTransaction(request);

        settlement.setStatus(SettlementStatus.COMPLETED);
        settlement.setCompletedAt(Instant.now());
        settlement.setJournalEntryId(entryResponse.getId());
        debtSettlementRepository.save(settlement);

        log.info("Successfully settled debt {} (code={}) via journal entry {}",
                settlementId, settlement.getSettlementCode(), entryResponse.getId());

        return entryResponse;
    }

    private String buildVietQrUrl(GroupMember toMember, BigDecimal amount, String code) {
        String bank = (toMember.getBankCode() != null && !toMember.getBankCode().isBlank())
                ? toMember.getBankCode() : "VCB";
        String acc = (toMember.getBankAccountNumber() != null && !toMember.getBankAccountNumber().isBlank())
                ? toMember.getBankAccountNumber() : "0000";
        String amt = amount.setScale(0, RoundingMode.HALF_EVEN).toPlainString();
        return String.format("https://img.vietqr.io/image/%s-%s-compact2.png?amount=%s&addInfo=%s",
                bank, acc, amt, code);
    }

    private String generateSettlementCode() {
        StringBuilder sb = new StringBuilder("FLV");
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
