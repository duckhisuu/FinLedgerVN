package vn.finledger.splitbill.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
import vn.finledger.splitbill.dto.*;
import vn.finledger.splitbill.event.DebtSettledEvent;
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
    private final ApplicationEventPublisher eventPublisher;

    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Creates a new expense group with a unique public share token.
     */
    @Transactional
    public ExpenseGroup createGroup(String name, String currency, boolean simplifyDebts) {
        return createGroupForUser(null, name, currency, simplifyDebts);
    }

    @Transactional
    public ExpenseGroup createGroupForUser(UUID ownerUserId, String name, String currency, boolean simplifyDebts) {
        String token = UUID.randomUUID().toString().replace("-", "");
        ExpenseGroup group = ExpenseGroup.builder()
                .ownerUserId(ownerUserId)
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
                .userId(group.getOwnerUserId())
                .name(accountName)
                .accountType(AccountType.ASSET)
                .accountSubtype("GROUP_DEBT")
                .includeInTotal(false)
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

        // Clear stale PENDING settlements before saving freshly computed plan
        List<DebtSettlement> existingPending = debtSettlementRepository.findByGroupIdAndStatus(groupId, SettlementStatus.PENDING);
        if (!existingPending.isEmpty()) {
            debtSettlementRepository.deleteAll(existingPending);
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

        // Publish Domain Event for Real-Time SSE Broadcasting
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new DebtSettledEvent(
                    settlement.getGroup().getId(),
                    settlement.getGroup().getShareToken(),
                    settlement.getId(),
                    settlement.getSettlementCode(),
                    fromMember.getId(),
                    toMember.getId(),
                    amount,
                    settlement.getCompletedAt()
            ));
        }

        return entryResponse;
    }

    @Transactional(readOnly = true)
    public List<ExpenseGroup> getAllGroups() {
        return expenseGroupRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ExpenseGroup> getGroupsForUser(UUID userId) {
        return expenseGroupRepository.findByOwnerUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public ExpenseGroup getGroupByIdForUser(UUID id, UUID userId) {
        return expenseGroupRepository.findByIdAndOwnerUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm hoặc bạn không có quyền truy cập"));
    }

    @Transactional(readOnly = true)
    public ExpenseGroup getGroupById(UUID id) {
        return expenseGroupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense group not found: " + id));
    }

    @Transactional(readOnly = true)
    public ExpenseGroup getGroupByShareToken(String shareToken) {
        return expenseGroupRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new IllegalArgumentException("Expense group not found for token: " + shareToken));
    }

    @Transactional(readOnly = true)
    public List<GroupMember> getGroupMembers(UUID groupId) {
        return groupMemberRepository.findByGroupId(groupId);
    }

    @Transactional(readOnly = true)
    public List<GroupExpense> getGroupExpenses(UUID groupId) {
        return groupExpenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
    }

    @Transactional(readOnly = true)
    public List<DebtSettlementDto> getExistingSettlements(UUID groupId) {
        List<DebtSettlement> list = debtSettlementRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
        return list.stream()
                .map(s -> DebtSettlementDto.builder()
                        .id(s.getId())
                        .fromMemberId(s.getFromMember().getId())
                        .fromMemberName(s.getFromMember().getDisplayName())
                        .toMemberId(s.getToMember().getId())
                        .toMemberName(s.getToMember().getDisplayName())
                        .toBankCode(s.getToMember().getBankCode())
                        .toBankAccountNumber(s.getToMember().getBankAccountNumber())
                        .amount(s.getAmount())
                        .settlementCode(s.getSettlementCode())
                        .status(s.getStatus().name())
                        .vietQrUrl(buildVietQrUrl(s.getToMember(), s.getAmount(), s.getSettlementCode()))
                        .build())
                .toList();
    }

    @Transactional
    public PublicGroupShareResponse getPublicGroupDetails(String shareToken) {
        ExpenseGroup group = getGroupByShareToken(shareToken);
        return buildGroupShareResponse(group);
    }

    @Transactional
    public PublicGroupShareResponse getGroupDetails(UUID groupId) {
        ExpenseGroup group = getGroupById(groupId);
        return buildGroupShareResponse(group);
    }

    private PublicGroupShareResponse buildGroupShareResponse(ExpenseGroup group) {
        List<GroupMember> members = groupMemberRepository.findByGroupId(group.getId());
        List<GroupExpense> expenses = groupExpenseRepository.findByGroupIdOrderByCreatedAtDesc(group.getId());

        Map<UUID, BigDecimal> netBalances = new HashMap<>();
        List<GroupMemberDto> memberDtos = new ArrayList<>();

        for (GroupMember m : members) {
            Account account = accountRepository.findById(m.getMemberLedgerAccountId()).orElse(null);
            BigDecimal netBal = (account != null && account.getCachedBalance() != null)
                    ? account.getCachedBalance().negate()
                    : BigDecimal.ZERO.setScale(4);

            netBalances.put(m.getId(), netBal);

            memberDtos.add(GroupMemberDto.builder()
                    .id(m.getId())
                    .displayName(m.getDisplayName())
                    .isGuest(m.isGuest())
                    .bankCode(m.getBankCode())
                    .bankAccountNumber(m.getBankAccountNumber())
                    .memberLedgerAccountId(m.getMemberLedgerAccountId())
                    .netBalance(netBal)
                    .build());
        }

        List<GroupExpenseDto> expenseDtos = new ArrayList<>();
        for (GroupExpense exp : expenses) {
            List<GroupExpenseShare> shares = groupExpenseShareRepository.findByGroupExpenseId(exp.getId());
            List<GroupExpenseShareDto> shareDtos = shares.stream()
                    .map(s -> GroupExpenseShareDto.builder()
                            .memberId(s.getMember().getId())
                            .memberName(s.getMember().getDisplayName())
                            .shareAmount(s.getShareAmount())
                            .rawWeight(s.getRawWeight())
                            .build())
                    .toList();

            expenseDtos.add(GroupExpenseDto.builder()
                    .id(exp.getId())
                    .title(exp.getTitle())
                    .totalAmount(exp.getTotalAmount())
                    .splitStrategy(exp.getSplitStrategy().name())
                    .paidByMemberId(exp.getPaidByMember().getId())
                    .paidByMemberName(exp.getPaidByMember().getDisplayName())
                    .createdAt(exp.getCreatedAt())
                    .shares(shareDtos)
                    .build());
        }

        List<DebtSettlementDto> settlements = getExistingSettlements(group.getId());
        if (settlements.isEmpty() && !expenses.isEmpty()) {
            settlements = generateSettlementPlan(group.getId());
        }

        return PublicGroupShareResponse.builder()
                .groupId(group.getId())
                .groupName(group.getName())
                .currency(group.getCurrency())
                .shareToken(group.getShareToken())
                .simplifyDebts(group.isSimplifyDebts())
                .createdAt(group.getCreatedAt())
                .members(memberDtos)
                .expenses(expenseDtos)
                .netBalances(netBalances)
                .settlements(settlements)
                .build();
    }

    @Transactional
    public DebtSettlementDto simulateWebhookSettlement(String shareToken, String settlementCode) {
        ExpenseGroup group = getGroupByShareToken(shareToken);

        DebtSettlement settlement = debtSettlementRepository.findBySettlementCode(settlementCode)
                .orElseThrow(() -> new IllegalArgumentException("Settlement code not found: " + settlementCode));

        if (!settlement.getGroup().getId().equals(group.getId())) {
            throw new IllegalArgumentException("Settlement does not belong to this group");
        }

        if (settlement.getStatus() == SettlementStatus.PENDING) {
            settleDebt(settlement.getId());
            settlement = debtSettlementRepository.findById(settlement.getId()).orElseThrow();
        }

        return DebtSettlementDto.builder()
                .id(settlement.getId())
                .fromMemberId(settlement.getFromMember().getId())
                .fromMemberName(settlement.getFromMember().getDisplayName())
                .toMemberId(settlement.getToMember().getId())
                .toMemberName(settlement.getToMember().getDisplayName())
                .toBankCode(settlement.getToMember().getBankCode())
                .toBankAccountNumber(settlement.getToMember().getBankAccountNumber())
                .amount(settlement.getAmount())
                .settlementCode(settlement.getSettlementCode())
                .status(settlement.getStatus().name())
                .vietQrUrl(buildVietQrUrl(settlement.getToMember(), settlement.getAmount(), settlement.getSettlementCode()))
                .build();
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
