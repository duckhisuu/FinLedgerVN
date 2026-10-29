package vn.finledger.finance.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.finledger.auth.security.CurrentUserProvider;
import vn.finledger.finance.dto.*;
import vn.finledger.finance.service.PersonalFinanceService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
public class PersonalFinanceController {

    private final PersonalFinanceService personalFinanceService;
    private final CurrentUserProvider currentUserProvider;

    // --- Home Overview & Reports ---

    @GetMapping("/overview")
    public ResponseEntity<HomeOverviewResponse> getHomeOverview() {
        UUID userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(personalFinanceService.getHomeOverview(userId));
    }

    @GetMapping("/reports")
    public ResponseEntity<FinancialReportsResponse> getReports(
            @RequestParam(value = "month", required = false) String month) {
        UUID userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(personalFinanceService.getFinancialReports(userId, month));
    }

    // --- Wallets ---

    @GetMapping("/wallets")
    public ResponseEntity<WalletsSummaryResponse> getWallets() {
        UUID userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(personalFinanceService.getWallets(userId));
    }

    @PostMapping("/wallets")
    public ResponseEntity<WalletDto> createWallet(@Valid @RequestBody CreateWalletRequest request) {
        UUID userId = currentUserProvider.requireUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(personalFinanceService.createWallet(userId, request));
    }

    @PutMapping("/wallets/{id}")
    public ResponseEntity<WalletDto> updateWallet(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWalletRequest request) {
        UUID userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(personalFinanceService.updateWallet(userId, id, request));
    }

    @DeleteMapping("/wallets/{id}")
    public ResponseEntity<Map<String, String>> archiveWallet(@PathVariable UUID id) {
        UUID userId = currentUserProvider.requireUserId();
        personalFinanceService.archiveWallet(userId, id);
        return ResponseEntity.ok(Map.of("message", "Đã lưu trữ ví thành công."));
    }

    // --- Categories ---

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> getCategories() {
        UUID userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(personalFinanceService.getCategories(userId));
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        UUID userId = currentUserProvider.requireUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(personalFinanceService.createCategory(userId, request));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Map<String, String>> archiveCategory(@PathVariable UUID id) {
        UUID userId = currentUserProvider.requireUserId();
        personalFinanceService.archiveCategory(userId, id);
        return ResponseEntity.ok(Map.of("message", "Đã xóa danh mục thành công."));
    }

    // --- Transactions ---

    @GetMapping("/transactions")
    public ResponseEntity<TransactionsFeedResponse> getTransactions(
            @RequestParam(value = "month", required = false) String month,
            @RequestParam(value = "walletId", required = false) UUID walletId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "includeCancelled", defaultValue = "false") boolean includeCancelled) {
        UUID userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(personalFinanceService.getTransactionsFeed(
                userId, month, walletId, type, search, includeCancelled));
    }

    @PostMapping("/transactions")
    public ResponseEntity<PersonalTransactionDto> recordTransaction(
            @Valid @RequestBody RecordPersonalTransactionRequest request) {
        UUID userId = currentUserProvider.requireUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(personalFinanceService.recordTransaction(userId, request));
    }

    @PutMapping("/transactions/{id}")
    public ResponseEntity<PersonalTransactionDto> updateTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody RecordPersonalTransactionRequest request) {
        UUID userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(personalFinanceService.updateTransaction(userId, id, request));
    }

    @DeleteMapping("/transactions/{id}")
    public ResponseEntity<Map<String, String>> cancelTransaction(
            @PathVariable UUID id,
            @RequestParam(value = "reason", required = false) String reason) {
        UUID userId = currentUserProvider.requireUserId();
        personalFinanceService.cancelTransaction(userId, id, reason);
        return ResponseEntity.ok(Map.of("message", "Đã hủy giao dịch thành công."));
    }

    // --- Budgets ---

    @GetMapping("/budgets")
    public ResponseEntity<BudgetsOverviewResponse> getBudgets(
            @RequestParam(value = "month", required = false) String month) {
        UUID userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(personalFinanceService.getBudgetsOverview(userId, month));
    }

    @PostMapping("/budgets")
    public ResponseEntity<BudgetDto> upsertBudget(@Valid @RequestBody UpsertBudgetRequest request) {
        UUID userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(personalFinanceService.upsertBudget(userId, request));
    }

    @DeleteMapping("/budgets/{id}")
    public ResponseEntity<Map<String, String>> deleteBudget(@PathVariable UUID id) {
        UUID userId = currentUserProvider.requireUserId();
        personalFinanceService.deleteBudget(userId, id);
        return ResponseEntity.ok(Map.of("message", "Đã xóa ngân sách thành công."));
    }

    // --- Receipt OCR Scan & Confirm ---

    @PostMapping(value = "/receipts/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptPreviewResponse> scanReceiptPreview(
            @RequestParam("image") MultipartFile image) throws Exception {
        UUID userId = currentUserProvider.requireUserId();
        return ResponseEntity.ok(personalFinanceService.previewReceipt(userId, image));
    }

    @PostMapping("/receipts/confirm")
    public ResponseEntity<PersonalTransactionDto> confirmReceipt(
            @Valid @RequestBody ConfirmReceiptRequest request) {
        UUID userId = currentUserProvider.requireUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(personalFinanceService.confirmReceipt(userId, request));
    }
}

