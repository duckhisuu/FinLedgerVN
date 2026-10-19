package vn.finledger.ledger.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.finledger.ledger.dto.*;
import vn.finledger.ledger.service.LedgerService;

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
