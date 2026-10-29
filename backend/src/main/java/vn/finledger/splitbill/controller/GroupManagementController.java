package vn.finledger.splitbill.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.finledger.auth.security.CurrentUserProvider;
import vn.finledger.ledger.dto.JournalEntryResponse;
import vn.finledger.splitbill.domain.ExpenseGroup;
import vn.finledger.splitbill.domain.GroupExpense;
import vn.finledger.splitbill.domain.GroupMember;
import vn.finledger.splitbill.dto.*;
import vn.finledger.splitbill.service.GroupSplitService;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupManagementController {

    private final GroupSplitService groupSplitService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<List<ExpenseGroup>> getAllGroups() {
        return currentUserProvider.findUserId()
                .map(userId -> ResponseEntity.ok(groupSplitService.getGroupsForUser(userId)))
                .orElseGet(() -> ResponseEntity.ok(groupSplitService.getAllGroups()));
    }

    @PostMapping
    public ResponseEntity<ExpenseGroup> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        UUID ownerUserId = currentUserProvider.findUserId().orElse(null);
        ExpenseGroup group = groupSplitService.createGroupForUser(
                ownerUserId,
                request.getName(),
                request.getCurrency(),
                request.isSimplifyDebts()
        );
        return ResponseEntity.ok(group);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicGroupShareResponse> getGroupDetails(@PathVariable UUID id) {
        currentUserProvider.findUserId().ifPresent(userId -> groupSplitService.getGroupByIdForUser(id, userId));
        return ResponseEntity.ok(groupSplitService.getGroupDetails(id));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<GroupMember> addMember(
            @PathVariable UUID id,
            @Valid @RequestBody AddMemberRequest request) {
        currentUserProvider.findUserId().ifPresent(userId -> groupSplitService.getGroupByIdForUser(id, userId));

        GroupMember member = groupSplitService.addMember(
                id,
                request.getDisplayName(),
                request.isGuest(),
                request.getBankCode(),
                request.getBankAccountNumber()
        );
        return ResponseEntity.ok(member);
    }

    @PostMapping("/{id}/expenses")
    public ResponseEntity<GroupExpense> recordExpense(
            @PathVariable UUID id,
            @Valid @RequestBody RecordExpenseRequest request) {
        currentUserProvider.findUserId().ifPresent(userId -> groupSplitService.getGroupByIdForUser(id, userId));

        GroupExpense expense = groupSplitService.recordGroupExpense(
                id,
                request.getPaidByMemberId(),
                request.getTitle(),
                request.getTotalAmount(),
                request.getSplitStrategy(),
                request.getSplitInputs()
        );
        return ResponseEntity.ok(expense);
    }

    @GetMapping("/{id}/settlements")
    public ResponseEntity<List<DebtSettlementDto>> getOrGenerateSettlements(@PathVariable UUID id) {
        currentUserProvider.findUserId().ifPresent(userId -> groupSplitService.getGroupByIdForUser(id, userId));
        List<DebtSettlementDto> settlements = groupSplitService.getExistingSettlements(id);
        if (settlements.isEmpty()) {
            settlements = groupSplitService.generateSettlementPlan(id);
        }
        return ResponseEntity.ok(settlements);
    }

    @PostMapping("/{id}/settlements/recalculate")
    public ResponseEntity<List<DebtSettlementDto>> forceRecalculateSettlements(@PathVariable UUID id) {
        currentUserProvider.findUserId().ifPresent(userId -> groupSplitService.getGroupByIdForUser(id, userId));
        List<DebtSettlementDto> plan = groupSplitService.generateSettlementPlan(id);
        return ResponseEntity.ok(plan);
    }

    @PostMapping("/settlements/{settlementId}/settle")
    public ResponseEntity<JournalEntryResponse> settleDebtManually(@PathVariable UUID settlementId) {
        JournalEntryResponse response = groupSplitService.settleDebt(settlementId);
        return ResponseEntity.ok(response);
    }
}
