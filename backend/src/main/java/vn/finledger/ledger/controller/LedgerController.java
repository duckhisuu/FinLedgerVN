package vn.finledger.ledger.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.finledger.ledger.domain.AccountType;
import vn.finledger.ledger.dto.*;
import vn.finledger.ledger.service.LedgerService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    @PostMapping("/transactions")
    public ResponseEntity<JournalEntryResponse> recordTransaction(
            @Valid @RequestBody RecordTransactionRequest request) {
        JournalEntryResponse response = ledgerService.recordTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/transactions/{id}/reverse")
    public ResponseEntity<JournalEntryResponse> reverseTransaction(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        JournalEntryResponse response = ledgerService.reverseTransaction(id, reason);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions/{id}")
    public ResponseEntity<JournalEntryResponse> getTransaction(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ledgerService.getJournalEntry(id));
    }

    @GetMapping("/entries")
    public ResponseEntity<List<JournalEntryResponse>> getAllEntries() {
        return ResponseEntity.ok(ledgerService.getAllJournalEntries());
    }

    @GetMapping("/entries/{id}")
    public ResponseEntity<JournalEntryResponse> getEntryById(@PathVariable UUID id) {
        return ResponseEntity.ok(ledgerService.getJournalEntry(id));
    }

    @PostMapping("/entries/{id}/reverse")
    public ResponseEntity<JournalEntryResponse> reverseEntry(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = (body != null && body.containsKey("reason")) ? body.get("reason") : "Manual user reversal";
        JournalEntryResponse reversed = ledgerService.reverseTransaction(id, reason);
        return ResponseEntity.ok(reversed);
    }

    @GetMapping("/equation")
    public ResponseEntity<AccountingEquationResponse> getAccountingEquation() {
        List<AccountResponse> accounts = ledgerService.getAllAccounts();

        BigDecimal assets = BigDecimal.ZERO;
        BigDecimal liabilities = BigDecimal.ZERO;
        BigDecimal equity = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (AccountResponse acc : accounts) {
            BigDecimal bal = acc.getCachedBalance() != null ? acc.getCachedBalance() : BigDecimal.ZERO;
            AccountType type = acc.getAccountType();

            if (bal.compareTo(BigDecimal.ZERO) >= 0) {
                totalDebit = totalDebit.add(bal);
            } else {
                totalCredit = totalCredit.add(bal.abs());
            }

            switch (type) {
                case ASSET -> assets = assets.add(bal);
                case LIABILITY -> liabilities = liabilities.add(bal);
                case EQUITY -> equity = equity.add(bal);
                case REVENUE -> revenue = revenue.add(bal);
                case EXPENSE -> expense = expense.add(bal);
            }
        }

        BigDecimal sumDifference = assets.add(liabilities).add(equity).add(revenue).add(expense)
                .setScale(4, RoundingMode.HALF_EVEN);
        boolean balanced = sumDifference.compareTo(BigDecimal.ZERO) == 0;

        return ResponseEntity.ok(AccountingEquationResponse.builder()
                .totalAssets(assets.setScale(4, RoundingMode.HALF_EVEN))
                .totalLiabilities(liabilities.setScale(4, RoundingMode.HALF_EVEN))
                .totalEquity(equity.setScale(4, RoundingMode.HALF_EVEN))
                .totalRevenue(revenue.setScale(4, RoundingMode.HALF_EVEN))
                .totalExpense(expense.setScale(4, RoundingMode.HALF_EVEN))
                .totalDebit(totalDebit.setScale(4, RoundingMode.HALF_EVEN))
                .totalCredit(totalCredit.setScale(4, RoundingMode.HALF_EVEN))
                .sumDifference(sumDifference)
                .balanced(balanced)
                .statusMessage(balanced ? "IMMUTABLE & BALANCED" : "UNBALANCED")
                .build());
    }

    @PostMapping("/accounts")
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = ledgerService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ledgerService.getAccount(id));
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        return ResponseEntity.ok(ledgerService.getAllAccounts());
    }
}
