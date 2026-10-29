package vn.finledger.finance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.finledger.finance.domain.Budget;
import vn.finledger.finance.dto.*;
import vn.finledger.finance.repository.BudgetRepository;
import vn.finledger.ingestion.domain.BankAccountMapping;
import vn.finledger.ingestion.ocr.*;
import vn.finledger.ingestion.repository.BankAccountMappingRepository;
import vn.finledger.ingestion.service.LocalTrainableCategorizer;
import vn.finledger.ledger.domain.Account;
import vn.finledger.ledger.domain.AccountType;
import vn.finledger.ledger.domain.JournalEntry;
import vn.finledger.ledger.domain.PostingLeg;
import vn.finledger.ledger.dto.AccountResponse;
import vn.finledger.ledger.dto.CreateAccountRequest;
import vn.finledger.ledger.dto.JournalEntryResponse;
import vn.finledger.ledger.dto.PostingLegRequest;
import vn.finledger.ledger.dto.RecordTransactionRequest;
import vn.finledger.ledger.exception.AccountNotFoundException;
import vn.finledger.ledger.exception.TransactionNotFoundException;
import vn.finledger.ledger.repository.AccountRepository;
import vn.finledger.ledger.repository.JournalEntryRepository;
import vn.finledger.ledger.service.LedgerService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalFinanceService {

    public static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Set<String> WALLET_SUBTYPES = Set.of("CASH", "BANK", "EWALLET", "CREDIT_CARD", "SAVINGS");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal THREE = new BigDecimal("3");
    private static final BigDecimal ALERT_THRESHOLD = new BigDecimal("80");

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final BudgetRepository budgetRepository;
    private final BankAccountMappingRepository bankAccountMappingRepository;
    private final LedgerService ledgerService;
    private final OcrVisionClient ocrVisionClient;
    private final SpatialReceiptLayoutParser spatialReceiptLayoutParser;
    private final VietnameseReceiptLineItemExtractor receiptLineItemExtractor;
    private final LocalTrainableCategorizer localTrainableCategorizer;

    // =========================================================================
    // 1. WALLETS
    // =========================================================================

    @Transactional(readOnly = true)
    public WalletsSummaryResponse getWallets(UUID userId) {
        List<Account> userAccounts = accountRepository.findByUserIdAndArchivedFalseOrderByCreatedAtAsc(userId);
        List<WalletDto> wallets = userAccounts.stream()
                .filter(this::isWalletAccount)
                .map(this::mapToWalletDto)
                .toList();

        BigDecimal totalBalance = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal totalAssets = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal totalCreditDebt = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);

        for (WalletDto w : wallets) {
            if (w.isIncludeInTotal()) {
                totalBalance = totalBalance.add(w.getBalance()).setScale(4, RoundingMode.HALF_EVEN);
            }
            if (w.getBalance().compareTo(BigDecimal.ZERO) >= 0) {
                totalAssets = totalAssets.add(w.getBalance()).setScale(4, RoundingMode.HALF_EVEN);
            } else {
                totalCreditDebt = totalCreditDebt.add(w.getBalance().abs()).setScale(4, RoundingMode.HALF_EVEN);
            }
        }

        return WalletsSummaryResponse.builder()
                .totalBalance(totalBalance)
                .totalAssets(totalAssets)
                .totalCreditDebt(totalCreditDebt)
                .wallets(wallets)
                .build();
    }

    @Transactional
    public WalletDto createWallet(UUID userId, CreateWalletRequest request) {
        String subtype = normalizeWalletSubtype(request.getSubtype());
        AccountType accountType = "CREDIT_CARD".equals(subtype) ? AccountType.LIABILITY : AccountType.ASSET;
        boolean allowNeg = request.getAllowNegative() != null
                ? request.getAllowNegative()
                : "CREDIT_CARD".equals(subtype);

        String defaultIcon = switch (subtype) {
            case "BANK" -> "landmark";
            case "EWALLET" -> "smartphone";
            case "CREDIT_CARD" -> "credit-card";
            case "SAVINGS" -> "piggy-bank";
            default -> "wallet";
        };
        String defaultColor = switch (subtype) {
            case "BANK" -> "#22c55e";
            case "EWALLET" -> "#ec4899";
            case "CREDIT_CARD" -> "#8b5cf6";
            case "SAVINGS" -> "#06b6d4";
            default -> "#f97316";
        };

        // Always create with 0.0000 first, then post Double-Entry Opening Balance if initialBalance != 0
        AccountResponse created = ledgerService.createAccount(CreateAccountRequest.builder()
                .userId(userId)
                .name(request.getName().trim())
                .accountType(accountType)
                .accountSubtype(subtype)
                .currency("VND")
                .initialBalance(BigDecimal.ZERO)
                .allowNegative(allowNeg)
                .icon(request.getIcon() != null && !request.getIcon().isBlank() ? request.getIcon() : defaultIcon)
                .color(request.getColor() != null && !request.getColor().isBlank() ? request.getColor() : defaultColor)
                .bankCode(blankToNull(request.getBankCode()))
                .accountNumber(blankToNull(request.getAccountNumber()))
                .includeInTotal(request.isIncludeInTotal())
                .build());

        BigDecimal initialBal = request.getInitialBalance() != null
                ? request.getInitialBalance().setScale(4, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);

        if (initialBal.compareTo(BigDecimal.ZERO) != 0) {
            Account equityAccount = getOrCreateSystemEquityAccount(userId);
            ledgerService.recordTransaction(RecordTransactionRequest.builder()
                    .userId(userId)
                    .entryType("OPENING_BALANCE")
                    .walletAccountId(created.getId())
                    .categoryAccountId(equityAccount.getId())
                    .transactionDate(Instant.now())
                    .description("Số dư ban đầu - " + created.getName())
                    .referenceId("OPENING:" + created.getId())
                    .postingLegs(List.of(
                            PostingLegRequest.builder()
                                    .accountId(created.getId())
                                    .amount(initialBal)
                                    .build(),
                            PostingLegRequest.builder()
                                    .accountId(equityAccount.getId())
                                    .amount(initialBal.negate())
                                    .build()
                    ))
                    .build());
        }

        syncBankAccountMappingIfPresent(userId, created.getId(), request.getBankCode(), request.getAccountNumber());

        Account refreshed = accountRepository.findById(created.getId()).orElseThrow();
        return mapToWalletDto(refreshed);
    }

    @Transactional
    public WalletDto updateWallet(UUID userId, UUID walletId, UpdateWalletRequest request) {
        Account wallet = requireOwnedAccount(userId, walletId);
        if (!isWalletAccount(wallet)) {
            throw new IllegalArgumentException("Tài khoản này không phải là ví cá nhân.");
        }

        wallet.setName(request.getName().trim());
        if (request.getIcon() != null && !request.getIcon().isBlank()) {
            wallet.setIcon(request.getIcon().trim());
        }
        if (request.getColor() != null && !request.getColor().isBlank()) {
            wallet.setColor(request.getColor().trim());
        }
        if (request.getBankCode() != null) {
            wallet.setBankCode(blankToNull(request.getBankCode()));
        }
        if (request.getAccountNumber() != null) {
            wallet.setAccountNumber(blankToNull(request.getAccountNumber()));
        }
        if (request.getIncludeInTotal() != null) {
            wallet.setIncludeInTotal(request.getIncludeInTotal());
        }
        if (request.getAllowNegative() != null) {
            wallet.setAllowNegative(request.getAllowNegative());
        }
        accountRepository.save(wallet);

        // Optional Double-Entry Balance Adjustment
        if (request.getTargetBalance() != null) {
            BigDecimal target = request.getTargetBalance().setScale(4, RoundingMode.HALF_EVEN);
            BigDecimal current = wallet.getCachedBalance().setScale(4, RoundingMode.HALF_EVEN);
            BigDecimal delta = target.subtract(current).setScale(4, RoundingMode.HALF_EVEN);
            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                Account equityAccount = getOrCreateSystemEquityAccount(userId);
                ledgerService.recordTransaction(RecordTransactionRequest.builder()
                        .userId(userId)
                        .entryType("ADJUSTMENT")
                        .walletAccountId(wallet.getId())
                        .categoryAccountId(equityAccount.getId())
                        .transactionDate(Instant.now())
                        .description("Điều chỉnh số dư ví " + wallet.getName())
                        .referenceId("ADJ:" + UUID.randomUUID())
                        .postingLegs(List.of(
                                PostingLegRequest.builder()
                                        .accountId(wallet.getId())
                                        .amount(delta)
                                        .build(),
                                PostingLegRequest.builder()
                                        .accountId(equityAccount.getId())
                                        .amount(delta.negate())
                                        .build()
                        ))
                        .build());
            }
        }

        syncBankAccountMappingIfPresent(userId, wallet.getId(), wallet.getBankCode(), wallet.getAccountNumber());

        Account refreshed = accountRepository.findById(wallet.getId()).orElseThrow();
        return mapToWalletDto(refreshed);
    }

    @Transactional
    public void archiveWallet(UUID userId, UUID walletId) {
        Account wallet = requireOwnedAccount(userId, walletId);
        if (!isWalletAccount(wallet)) {
            throw new IllegalArgumentException("Tài khoản này không phải là ví cá nhân.");
        }
        wallet.setArchived(true);
        wallet.setIncludeInTotal(false);
        accountRepository.save(wallet);
    }

    // =========================================================================
    // 2. CATEGORIES
    // =========================================================================

    @Transactional(readOnly = true)
    public List<CategoryDto> getCategories(UUID userId) {
        return accountRepository.findByUserIdAndArchivedFalseOrderByCreatedAtAsc(userId).stream()
                .filter(this::isCategoryAccount)
                .map(this::mapToCategoryDto)
                .toList();
    }

    @Transactional
    public CategoryDto createCategory(UUID userId, CreateCategoryRequest request) {
        String type = request.getType().trim().toUpperCase();
        boolean isIncome = "INCOME".equals(type) || "REVENUE".equals(type);
        AccountType accType = isIncome ? AccountType.REVENUE : AccountType.EXPENSE;
        String subtype = isIncome ? "CATEGORY_INCOME" : "CATEGORY_EXPENSE";

        AccountResponse created = ledgerService.createAccount(CreateAccountRequest.builder()
                .userId(userId)
                .name(request.getName().trim())
                .accountType(accType)
                .accountSubtype(subtype)
                .currency("VND")
                .initialBalance(BigDecimal.ZERO)
                .allowNegative(true)
                .includeInTotal(false)
                .icon(request.getIcon() != null && !request.getIcon().isBlank() ? request.getIcon() : "tag")
                .color(request.getColor() != null && !request.getColor().isBlank()
                        ? request.getColor()
                        : (isIncome ? "#22c55e" : "#ef4444"))
                .build());

        return CategoryDto.builder()
                .id(created.getId())
                .name(created.getName())
                .type(isIncome ? "INCOME" : "EXPENSE")
                .icon(created.getIcon())
                .color(created.getColor())
                .build();
    }

    @Transactional
    public void archiveCategory(UUID userId, UUID categoryId) {
        Account category = requireOwnedAccount(userId, categoryId);
        if (!isCategoryAccount(category)) {
            throw new IllegalArgumentException("Tài khoản này không phải là danh mục thu/chi.");
        }
        category.setArchived(true);
        accountRepository.save(category);
    }

    // =========================================================================
    // 3. TRANSACTIONS (DOUBLE-ENTRY EXPENSE / INCOME / TRANSFER / REVERSAL)
    // =========================================================================

    @Transactional
    public PersonalTransactionDto recordTransaction(UUID userId, RecordPersonalTransactionRequest request) {
        String type = request.getType().trim().toUpperCase();
        BigDecimal amount = request.getAmount().setScale(4, RoundingMode.HALF_EVEN);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền giao dịch phải lớn hơn 0.");
        }

        Account wallet = requireOwnedAccount(userId, request.getWalletId());
        if (!isWalletAccount(wallet)) {
            throw new IllegalArgumentException("Ví nguồn không hợp lệ.");
        }

        Instant txDate = request.getTransactionDate() != null ? request.getTransactionDate() : Instant.now();
        List<PostingLegRequest> legs;
        UUID counterpartyId;
        String defaultDesc;

        switch (type) {
            case "EXPENSE" -> {
                if (request.getCategoryId() == null) {
                    throw new IllegalArgumentException("Vui lòng chọn danh mục chi tiêu.");
                }
                Account category = requireOwnedAccount(userId, request.getCategoryId());
                if (category.getAccountType() != AccountType.EXPENSE) {
                    throw new IllegalArgumentException("Danh mục đã chọn không phải danh mục chi tiêu.");
                }
                counterpartyId = category.getId();
                defaultDesc = category.getName();
                legs = List.of(
                        PostingLegRequest.builder().accountId(wallet.getId()).amount(amount.negate()).build(),
                        PostingLegRequest.builder().accountId(category.getId()).amount(amount).build()
                );
            }
            case "INCOME" -> {
                if (request.getCategoryId() == null) {
                    throw new IllegalArgumentException("Vui lòng chọn danh mục thu nhập.");
                }
                Account category = requireOwnedAccount(userId, request.getCategoryId());
                if (category.getAccountType() != AccountType.REVENUE) {
                    throw new IllegalArgumentException("Danh mục đã chọn không phải danh mục thu nhập.");
                }
                counterpartyId = category.getId();
                defaultDesc = category.getName();
                legs = List.of(
                        PostingLegRequest.builder().accountId(wallet.getId()).amount(amount).build(),
                        PostingLegRequest.builder().accountId(category.getId()).amount(amount.negate()).build()
                );
            }
            case "TRANSFER" -> {
                if (request.getToWalletId() == null) {
                    throw new IllegalArgumentException("Vui lòng chọn ví đích để chuyển tiền.");
                }
                if (request.getWalletId().equals(request.getToWalletId())) {
                    throw new IllegalArgumentException("Ví nguồn và ví đích phải khác nhau.");
                }
                Account toWallet = requireOwnedAccount(userId, request.getToWalletId());
                if (!isWalletAccount(toWallet)) {
                    throw new IllegalArgumentException("Ví đích không hợp lệ.");
                }
                counterpartyId = toWallet.getId();
                defaultDesc = "Chuyển tiền: " + wallet.getName() + " → " + toWallet.getName();
                legs = List.of(
                        PostingLegRequest.builder().accountId(wallet.getId()).amount(amount.negate()).build(),
                        PostingLegRequest.builder().accountId(toWallet.getId()).amount(amount).build()
                );
            }
            default -> throw new IllegalArgumentException("Loại giao dịch không hợp lệ: " + type);
        }

        String description = (request.getDescription() != null && !request.getDescription().isBlank())
                ? request.getDescription().trim()
                : defaultDesc;

        JournalEntryResponse recorded = ledgerService.recordTransaction(RecordTransactionRequest.builder()
                .userId(userId)
                .entryType(type)
                .walletAccountId(wallet.getId())
                .categoryAccountId(counterpartyId)
                .transactionDate(txDate)
                .description(description)
                .referenceId("TX:" + UUID.randomUUID())
                .postingLegs(legs)
                .build());

        Map<UUID, Account> accountMap = loadUserAccountMap(userId);
        JournalEntry entity = journalEntryRepository.findById(recorded.getId()).orElseThrow();
        return mapToPersonalTransactionDto(entity, accountMap, Collections.emptySet());
    }

    @Transactional
    public PersonalTransactionDto updateTransaction(
            UUID userId,
            UUID transactionId,
            RecordPersonalTransactionRequest updatedRequest) {
        JournalEntry existing = journalEntryRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        if (existing.getReversalOfId() != null || journalEntryRepository.existsByReversalOfId(transactionId)) {
            throw new IllegalArgumentException("Không thể chỉnh sửa giao dịch đã bị hủy.");
        }

        // Strict Immutability: Reverse original immutable entry, then record replacement entry atomically
        ledgerService.reverseTransaction(transactionId, "Cập nhật thông tin giao dịch");
        return recordTransaction(userId, updatedRequest);
    }

    @Transactional
    public void cancelTransaction(UUID userId, UUID transactionId, String reason) {
        JournalEntry existing = journalEntryRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
        if (existing.getReversalOfId() != null) {
            throw new IllegalArgumentException("Đây là bút toán hủy, không thể hủy tiếp.");
        }
        ledgerService.reverseTransaction(
                transactionId,
                reason != null && !reason.isBlank() ? reason.trim() : "Người dùng hủy giao dịch");
    }

    @Transactional(readOnly = true)
    public TransactionsFeedResponse getTransactionsFeed(
            UUID userId,
            String month,
            UUID walletId,
            String typeFilter,
            String search,
            boolean includeCancelled) {

        YearMonth ym = parseYearMonthOrCurrent(month);
        Instant start = ym.atDay(1).atStartOfDay(VN_ZONE).toInstant();
        Instant end = ym.plusMonths(1).atDay(1).atStartOfDay(VN_ZONE).toInstant();

        Map<UUID, Account> accountMap = loadUserAccountMap(userId);
        Set<UUID> reversedIds = journalEntryRepository.findReversedEntryIdsByUserId(userId);
        List<JournalEntry> entries = journalEntryRepository.findByUserIdAndDateRange(userId, start, end);

        String normalizedSearch = search != null && !search.isBlank()
                ? stripAccents(search.trim().toLowerCase())
                : null;

        List<PersonalTransactionDto> filtered = new ArrayList<>();
        BigDecimal totalIncome = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal totalExpense = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);

        for (JournalEntry entry : entries) {
            if (entry.getReversalOfId() != null) {
                continue; // Hide internal reversal plumbing rows; original row carries cancelled=true
            }
            boolean isCancelled = reversedIds.contains(entry.getId());
            if (isCancelled && !includeCancelled) {
                continue;
            }

            PersonalTransactionDto dto = mapToPersonalTransactionDto(entry, accountMap, reversedIds);
            if (dto == null) {
                continue;
            }

            if (walletId != null) {
                boolean matchesWallet = walletId.equals(dto.getWalletId()) || walletId.equals(dto.getToWalletId());
                if (!matchesWallet) continue;
            }

            if (typeFilter != null && !typeFilter.isBlank() && !"ALL".equalsIgnoreCase(typeFilter)) {
                if ("EXPENSE".equalsIgnoreCase(typeFilter)) {
                    if (!"EXPENSE".equals(dto.getType()) && !"RECEIPT_SCAN".equals(dto.getType())) continue;
                } else if (!typeFilter.equalsIgnoreCase(dto.getType())) {
                    continue;
                }
            }

            if (normalizedSearch != null) {
                String haystack = stripAccents((
                        (dto.getDescription() != null ? dto.getDescription() : "") + " " +
                        (dto.getCategoryName() != null ? dto.getCategoryName() : "") + " " +
                        (dto.getWalletName() != null ? dto.getWalletName() : "")
                ).toLowerCase());
                if (!haystack.contains(normalizedSearch)) continue;
            }

            filtered.add(dto);

            if (!isCancelled) {
                if ("INCOME".equals(dto.getType())) {
                    totalIncome = totalIncome.add(dto.getAmount()).setScale(4, RoundingMode.HALF_EVEN);
                } else if ("EXPENSE".equals(dto.getType()) || "RECEIPT_SCAN".equals(dto.getType())) {
                    totalExpense = totalExpense.add(dto.getAmount()).setScale(4, RoundingMode.HALF_EVEN);
                }
            }
        }

        BigDecimal netCashflow = totalIncome.subtract(totalExpense).setScale(4, RoundingMode.HALF_EVEN);

        return TransactionsFeedResponse.builder()
                .month(ym.format(YEAR_MONTH_FMT))
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netCashflow(netCashflow)
                .transactions(filtered)
                .build();
    }

    // =========================================================================
    // 4. HOME OVERVIEW (MONEY LOVER DASHBOARD) & SCIENTIFIC REPORTS
    // =========================================================================

    @Transactional(readOnly = true)
    public HomeOverviewResponse getHomeOverview(UUID userId) {
        WalletsSummaryResponse walletsSummary = getWallets(userId);
        Map<UUID, Account> accountMap = loadUserAccountMap(userId);
        Set<UUID> reversedIds = journalEntryRepository.findReversedEntryIdsByUserId(userId);

        LocalDate todayVn = LocalDate.now(VN_ZONE);
        YearMonth currentYm = YearMonth.from(todayVn);
        YearMonth prevStartYm = currentYm.minusMonths(3);

        Instant fourMonthStart = prevStartYm.atDay(1).atStartOfDay(VN_ZONE).toInstant();
        Instant nextMonthStart = currentYm.plusMonths(1).atDay(1).atStartOfDay(VN_ZONE).toInstant();

        List<JournalEntry> recentFourMonthEntries = journalEntryRepository.findByUserIdAndDateRange(
                userId, fourMonthStart, nextMonthStart);

        int daysInCurrentMonth = currentYm.lengthOfMonth();
        BigDecimal[] curDailySpent = initZeroArray(daysInCurrentMonth + 1);
        BigDecimal[] curDailyIncome = initZeroArray(daysInCurrentMonth + 1);

        // Previous 3 months daily totals (days 1..31)
        BigDecimal[] prev3DailySpentSum = initZeroArray(32);
        BigDecimal[] prev3DailyIncomeSum = initZeroArray(32);
        BigDecimal prev3TotalSpent = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal prev3TotalIncome = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);

        // Category breakdowns for Month and Week
        LocalDate weekStart = todayVn.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(7);

        Map<UUID, BigDecimal> monthCategorySpent = new HashMap<>();
        Map<UUID, BigDecimal> weekCategorySpent = new HashMap<>();

        for (JournalEntry entry : recentFourMonthEntries) {
            if (isEntryCancelledOrReversal(entry, reversedIds)) continue;

            ZonedDateTime zdt = entry.getTransactionDate().atZone(VN_ZONE);
            YearMonth entryYm = YearMonth.from(zdt);
            LocalDate entryDate = zdt.toLocalDate();
            int dayOfMonth = zdt.getDayOfMonth();

            for (PostingLeg leg : entry.getPostingLegs()) {
                Account acc = accountMap.get(leg.getAccount().getId());
                if (acc == null) continue;

                if (acc.getAccountType() == AccountType.EXPENSE && leg.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal amt = leg.getAmount().setScale(4, RoundingMode.HALF_EVEN);
                    if (entryYm.equals(currentYm)) {
                        curDailySpent[dayOfMonth] = curDailySpent[dayOfMonth].add(amt);
                        monthCategorySpent.merge(acc.getId(), amt, BigDecimal::add);
                    } else if (!entryYm.isBefore(prevStartYm) && entryYm.isBefore(currentYm)) {
                        prev3DailySpentSum[dayOfMonth] = prev3DailySpentSum[dayOfMonth].add(amt);
                        prev3TotalSpent = prev3TotalSpent.add(amt);
                    }

                    if (!entryDate.isBefore(weekStart) && entryDate.isBefore(weekEnd)) {
                        weekCategorySpent.merge(acc.getId(), amt, BigDecimal::add);
                    }
                } else if (acc.getAccountType() == AccountType.REVENUE && leg.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                    BigDecimal amt = leg.getAmount().abs().setScale(4, RoundingMode.HALF_EVEN);
                    if (entryYm.equals(currentYm)) {
                        curDailyIncome[dayOfMonth] = curDailyIncome[dayOfMonth].add(amt);
                    } else if (!entryYm.isBefore(prevStartYm) && entryYm.isBefore(currentYm)) {
                        prev3DailyIncomeSum[dayOfMonth] = prev3DailyIncomeSum[dayOfMonth].add(amt);
                        prev3TotalIncome = prev3TotalIncome.add(amt);
                    }
                }
            }
        }

        List<DailyTrendPointDto> dailyTrend = new ArrayList<>(daysInCurrentMonth);
        BigDecimal cumSpent = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal cumIncome = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal prevCumSpent = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal prevCumIncome = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);

        for (int d = 1; d <= daysInCurrentMonth; d++) {
            cumSpent = cumSpent.add(curDailySpent[d]).setScale(4, RoundingMode.HALF_EVEN);
            cumIncome = cumIncome.add(curDailyIncome[d]).setScale(4, RoundingMode.HALF_EVEN);

            BigDecimal avgDaySpent = prev3DailySpentSum[d].divide(THREE, 4, RoundingMode.HALF_EVEN);
            BigDecimal avgDayIncome = prev3DailyIncomeSum[d].divide(THREE, 4, RoundingMode.HALF_EVEN);
            prevCumSpent = prevCumSpent.add(avgDaySpent).setScale(4, RoundingMode.HALF_EVEN);
            prevCumIncome = prevCumIncome.add(avgDayIncome).setScale(4, RoundingMode.HALF_EVEN);

            dailyTrend.add(DailyTrendPointDto.builder()
                    .day(d)
                    .dailySpent(curDailySpent[d])
                    .dailyIncome(curDailyIncome[d])
                    .cumulativeSpent(cumSpent)
                    .cumulativeIncome(cumIncome)
                    .prev3MonthAvgCumulativeSpent(prevCumSpent)
                    .prev3MonthAvgCumulativeIncome(prevCumIncome)
                    .build());
        }

        BigDecimal prev3AvgSpent = prev3TotalSpent.divide(THREE, 4, RoundingMode.HALF_EVEN);
        BigDecimal prev3AvgIncome = prev3TotalIncome.divide(THREE, 4, RoundingMode.HALF_EVEN);

        List<TopSpendingItemDto> topMonth = buildCategoryBreakdownList(monthCategorySpent, accountMap);
        List<TopSpendingItemDto> topWeek = buildCategoryBreakdownList(weekCategorySpent, accountMap);

        // Recent 6 active transactions across all time
        List<JournalEntry> allUserEntries = journalEntryRepository.findByUserIdOrderByTransactionDateDescCreatedAtDesc(userId);
        List<PersonalTransactionDto> recentTx = new ArrayList<>();
        for (JournalEntry entry : allUserEntries) {
            if (isEntryCancelledOrReversal(entry, reversedIds)) continue;
            if ("OPENING_BALANCE".equals(entry.getEntryType())) continue;
            PersonalTransactionDto dto = mapToPersonalTransactionDto(entry, accountMap, reversedIds);
            if (dto != null) {
                recentTx.add(dto);
                if (recentTx.size() >= 6) break;
            }
        }

        // Budget Alerts (>= 80% used in current month)
        BudgetsOverviewResponse budgetsOverview = getBudgetsOverview(userId, currentYm.format(YEAR_MONTH_FMT));
        List<HomeOverviewResponse.BudgetAlertDto> alerts = budgetsOverview.getBudgets().stream()
                .filter(b -> b.getPercentage().compareTo(ALERT_THRESHOLD) >= 0)
                .map(b -> HomeOverviewResponse.BudgetAlertDto.builder()
                        .budgetId(b.getId())
                        .categoryName(b.getCategoryName())
                        .amountLimit(b.getAmountLimit())
                        .spentAmount(b.getSpentAmount())
                        .percentage(b.getPercentage())
                        .exceeded(b.isOverBudget())
                        .build())
                .toList();

        return HomeOverviewResponse.builder()
                .totalBalance(walletsSummary.getTotalBalance())
                .currentMonth(currentYm.format(YEAR_MONTH_FMT))
                .wallets(walletsSummary.getWallets())
                .monthSpent(cumSpent)
                .monthIncome(cumIncome)
                .prev3MonthAvgSpent(prev3AvgSpent)
                .prev3MonthAvgIncome(prev3AvgIncome)
                .dailyTrend(dailyTrend)
                .topSpendingMonth(topMonth)
                .topSpendingWeek(topWeek)
                .recentTransactions(recentTx)
                .budgetAlerts(alerts)
                .build();
    }

    @Transactional(readOnly = true)
    public FinancialReportsResponse getFinancialReports(UUID userId, String month) {
        YearMonth selectedYm = parseYearMonthOrCurrent(month);
        YearMonth startYm = selectedYm.minusMonths(5);

        Instant rangeStart = startYm.atDay(1).atStartOfDay(VN_ZONE).toInstant();
        Instant rangeEnd = selectedYm.plusMonths(1).atDay(1).atStartOfDay(VN_ZONE).toInstant();

        Map<UUID, Account> accountMap = loadUserAccountMap(userId);
        Set<UUID> reversedIds = journalEntryRepository.findReversedEntryIdsByUserId(userId);
        List<JournalEntry> entries = journalEntryRepository.findByUserIdAndDateRange(userId, rangeStart, rangeEnd);

        Map<YearMonth, BigDecimal> monthIncomes = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> monthExpenses = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            YearMonth ym = startYm.plusMonths(i);
            monthIncomes.put(ym, BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN));
            monthExpenses.put(ym, BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN));
        }

        Map<UUID, BigDecimal> selectedExpenseByCat = new HashMap<>();
        Map<UUID, BigDecimal> selectedIncomeByCat = new HashMap<>();

        for (JournalEntry entry : entries) {
            if (isEntryCancelledOrReversal(entry, reversedIds)) continue;
            YearMonth entryYm = YearMonth.from(entry.getTransactionDate().atZone(VN_ZONE));

            for (PostingLeg leg : entry.getPostingLegs()) {
                Account acc = accountMap.get(leg.getAccount().getId());
                if (acc == null) continue;

                if (acc.getAccountType() == AccountType.EXPENSE && leg.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal amt = leg.getAmount().setScale(4, RoundingMode.HALF_EVEN);
                    monthExpenses.computeIfPresent(entryYm, (k, v) -> v.add(amt));
                    if (entryYm.equals(selectedYm)) {
                        selectedExpenseByCat.merge(acc.getId(), amt, BigDecimal::add);
                    }
                } else if (acc.getAccountType() == AccountType.REVENUE && leg.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                    BigDecimal amt = leg.getAmount().abs().setScale(4, RoundingMode.HALF_EVEN);
                    monthIncomes.computeIfPresent(entryYm, (k, v) -> v.add(amt));
                    if (entryYm.equals(selectedYm)) {
                        selectedIncomeByCat.merge(acc.getId(), amt, BigDecimal::add);
                    }
                }
            }
        }

        List<FinancialReportsResponse.MonthlyCashflowItem> history = new ArrayList<>();
        for (YearMonth ym : monthIncomes.keySet()) {
            BigDecimal inc = monthIncomes.get(ym);
            BigDecimal exp = monthExpenses.get(ym);
            history.add(FinancialReportsResponse.MonthlyCashflowItem.builder()
                    .month(ym.format(YEAR_MONTH_FMT))
                    .label("T" + ym.getMonthValue() + "/" + (ym.getYear() % 100))
                    .income(inc)
                    .expense(exp)
                    .net(inc.subtract(exp).setScale(4, RoundingMode.HALF_EVEN))
                    .build());
        }

        BigDecimal selIncome = monthIncomes.getOrDefault(selectedYm, BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN));
        BigDecimal selExpense = monthExpenses.getOrDefault(selectedYm, BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN));
        BigDecimal netSavings = selIncome.subtract(selExpense).setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal savingsRate = selIncome.compareTo(BigDecimal.ZERO) > 0
                ? netSavings.multiply(HUNDRED).divide(selIncome, 2, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

        WalletsSummaryResponse walletsSummary = getWallets(userId);

        return FinancialReportsResponse.builder()
                .selectedMonth(selectedYm.format(YEAR_MONTH_FMT))
                .netWorth(walletsSummary.getTotalBalance())
                .totalIncome(selIncome)
                .totalExpense(selExpense)
                .netSavings(netSavings)
                .savingsRatePercent(savingsRate)
                .monthlyHistory(history)
                .expenseByCategory(buildCategoryBreakdownList(selectedExpenseByCat, accountMap))
                .incomeByCategory(buildCategoryBreakdownList(selectedIncomeByCat, accountMap))
                .wallets(walletsSummary.getWallets())
                .build();
    }

    // =========================================================================
    // 5. BUDGETS
    // =========================================================================

    @Transactional(readOnly = true)
    public BudgetsOverviewResponse getBudgetsOverview(UUID userId, String month) {
        YearMonth ym = parseYearMonthOrCurrent(month);
        String monthKey = ym.format(YEAR_MONTH_FMT);

        Instant start = ym.atDay(1).atStartOfDay(VN_ZONE).toInstant();
        Instant end = ym.plusMonths(1).atDay(1).atStartOfDay(VN_ZONE).toInstant();

        Map<UUID, Account> accountMap = loadUserAccountMap(userId);
        Set<UUID> reversedIds = journalEntryRepository.findReversedEntryIdsByUserId(userId);
        List<JournalEntry> entries = journalEntryRepository.findByUserIdAndDateRange(userId, start, end);

        Map<UUID, BigDecimal> categorySpent = new HashMap<>();
        BigDecimal totalMonthSpent = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);

        for (JournalEntry entry : entries) {
            if (isEntryCancelledOrReversal(entry, reversedIds)) continue;
            for (PostingLeg leg : entry.getPostingLegs()) {
                Account acc = accountMap.get(leg.getAccount().getId());
                if (acc != null && acc.getAccountType() == AccountType.EXPENSE && leg.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal amt = leg.getAmount().setScale(4, RoundingMode.HALF_EVEN);
                    categorySpent.merge(acc.getId(), amt, BigDecimal::add);
                    totalMonthSpent = totalMonthSpent.add(amt).setScale(4, RoundingMode.HALF_EVEN);
                }
            }
        }

        List<Budget> budgets = budgetRepository.findByUserIdAndMonthYearOrderByCreatedAtAsc(userId, monthKey);
        List<BudgetDto> budgetDtos = new ArrayList<>();
        BigDecimal sumCategoryLimits = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal explicitTotalLimit = null;

        for (Budget b : budgets) {
            BigDecimal limit = b.getAmountLimit().setScale(4, RoundingMode.HALF_EVEN);
            BigDecimal spent;
            String catName;
            String catIcon;
            String catColor;

            if (b.getCategoryAccountId() == null) {
                spent = totalMonthSpent;
                catName = "Tổng ngân sách tháng";
                catIcon = "layers";
                catColor = "#22c55e";
                explicitTotalLimit = limit;
            } else {
                Account cat = accountMap.get(b.getCategoryAccountId());
                if (cat == null) continue;
                spent = categorySpent.getOrDefault(cat.getId(), BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN));
                catName = cat.getName();
                catIcon = cat.getIcon() != null ? cat.getIcon() : "tag";
                catColor = cat.getColor() != null ? cat.getColor() : "#ef4444";
                sumCategoryLimits = sumCategoryLimits.add(limit).setScale(4, RoundingMode.HALF_EVEN);
            }

            BigDecimal remaining = limit.subtract(spent).setScale(4, RoundingMode.HALF_EVEN);
            BigDecimal pct = limit.compareTo(BigDecimal.ZERO) > 0
                    ? spent.multiply(HUNDRED).divide(limit, 2, RoundingMode.HALF_EVEN)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

            budgetDtos.add(BudgetDto.builder()
                    .id(b.getId())
                    .categoryId(b.getCategoryAccountId())
                    .categoryName(catName)
                    .categoryIcon(catIcon)
                    .categoryColor(catColor)
                    .monthYear(b.getMonthYear())
                    .amountLimit(limit)
                    .spentAmount(spent)
                    .remainingAmount(remaining)
                    .percentage(pct)
                    .overBudget(spent.compareTo(limit) > 0)
                    .note(b.getNote())
                    .build());
        }

        BigDecimal effectiveTotalLimit = explicitTotalLimit != null ? explicitTotalLimit : sumCategoryLimits;
        BigDecimal totalRemaining = effectiveTotalLimit.subtract(totalMonthSpent).setScale(4, RoundingMode.HALF_EVEN);

        LocalDate today = LocalDate.now(VN_ZONE);
        YearMonth currentYm = YearMonth.from(today);
        int daysInMonth = ym.lengthOfMonth();
        int daysRemaining;
        if (ym.equals(currentYm)) {
            daysRemaining = Math.max(1, daysInMonth - today.getDayOfMonth() + 1);
        } else if (ym.isAfter(currentYm)) {
            daysRemaining = daysInMonth;
        } else {
            daysRemaining = 0;
        }

        BigDecimal recommendedDaily = (daysRemaining > 0 && totalRemaining.compareTo(BigDecimal.ZERO) > 0)
                ? totalRemaining.divide(BigDecimal.valueOf(daysRemaining), 0, RoundingMode.HALF_EVEN).setScale(4, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);

        return BudgetsOverviewResponse.builder()
                .monthYear(monthKey)
                .daysInMonth(daysInMonth)
                .daysRemaining(daysRemaining)
                .totalBudgetLimit(effectiveTotalLimit)
                .totalSpentThisMonth(totalMonthSpent)
                .totalRemaining(totalRemaining)
                .recommendedDailySpend(recommendedDaily)
                .budgets(budgetDtos)
                .build();
    }

    @Transactional
    public BudgetDto upsertBudget(UUID userId, UpsertBudgetRequest request) {
        YearMonth ym = parseYearMonthOrCurrent(request.getMonthYear());
        String monthKey = ym.format(YEAR_MONTH_FMT);
        BigDecimal limit = request.getAmountLimit().setScale(4, RoundingMode.HALF_EVEN);

        if (request.getCategoryId() != null) {
            Account cat = requireOwnedAccount(userId, request.getCategoryId());
            if (cat.getAccountType() != AccountType.EXPENSE) {
                throw new IllegalArgumentException("Ngân sách chỉ áp dụng cho danh mục chi tiêu.");
            }
        }

        Optional<Budget> existingOpt = request.getCategoryId() != null
                ? budgetRepository.findByUserIdAndCategoryAccountIdAndMonthYear(userId, request.getCategoryId(), monthKey)
                : budgetRepository.findByUserIdAndCategoryAccountIdIsNullAndMonthYear(userId, monthKey);

        Budget budget = existingOpt.orElseGet(() -> Budget.builder()
                .userId(userId)
                .categoryAccountId(request.getCategoryId())
                .monthYear(monthKey)
                .currency("VND")
                .build());

        budget.setAmountLimit(limit);
        budget.setNote(blankToNull(request.getNote()));
        budgetRepository.save(budget);

        return getBudgetsOverview(userId, monthKey).getBudgets().stream()
                .filter(b -> b.getId().equals(budget.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void deleteBudget(UUID userId, UUID budgetId) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ngân sách."));
        budgetRepository.delete(budget);
    }

    // =========================================================================
    // 6. RECEIPT OCR PREVIEW & CONFIRM (USER-CONTROLLED WALLET & CATEGORIES)
    // =========================================================================

    @Transactional(readOnly = true)
    public ReceiptPreviewResponse previewReceipt(UUID userId, MultipartFile image) throws Exception {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ảnh hóa đơn hợp lệ.");
        }
        if (image.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Kích thước ảnh không được vượt quá 5MB.");
        }
        String ct = image.getContentType();
        if (ct == null || !Set.of("image/jpeg", "image/png", "image/webp").contains(ct.toLowerCase())) {
            throw new IllegalArgumentException("Chỉ hỗ trợ định dạng ảnh JPG, PNG hoặc WEBP.");
        }

        List<OcrTextBox> boxes = ocrVisionClient.extractTextBoxes(image.getBytes(), image.getOriginalFilename());
        if (boxes == null || boxes.isEmpty()) {
            throw new IllegalArgumentException("Không nhận diện được chữ trên ảnh hóa đơn. Vui lòng chụp rõ nét hơn.");
        }

        List<ReconstructedReceiptRow> rows = spatialReceiptLayoutParser.parse(boxes);
        ParsedReceiptResult parsed = receiptLineItemExtractor.extract(rows);
        if (parsed.getLineItems() == null || parsed.getLineItems().isEmpty()) {
            throw new IllegalArgumentException("Không tách được dòng sản phẩm từ hóa đơn. Bạn có thể nhập giao dịch thủ công.");
        }

        List<Account> expenseCategories = accountRepository
                .findByUserIdAndAccountTypeAndArchivedFalseOrderByCreatedAtAsc(userId, AccountType.EXPENSE);
        if (expenseCategories.isEmpty()) {
            throw new IllegalArgumentException("Bạn chưa có danh mục chi tiêu nào.");
        }

        List<ReceiptPreviewResponse.ReceiptPreviewItem> previewItems = new ArrayList<>();
        BigDecimal computedSum = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);

        for (ReceiptLineItemDto item : parsed.getLineItems()) {
            BigDecimal lineAmt = item.getLineAmount() != null
                    ? item.getLineAmount().setScale(4, RoundingMode.HALF_EVEN)
                    : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);
            if (lineAmt.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal qty = item.getQuantity() != null && item.getQuantity().compareTo(BigDecimal.ZERO) > 0
                    ? item.getQuantity()
                    : BigDecimal.ONE;
            BigDecimal unit = item.getUnitPrice() != null
                    ? item.getUnitPrice().setScale(4, RoundingMode.HALF_EVEN)
                    : lineAmt.divide(qty, 4, RoundingMode.HALF_EVEN);

            Account suggested = matchUserExpenseCategory(item.getProductName(), expenseCategories);
            computedSum = computedSum.add(lineAmt).setScale(4, RoundingMode.HALF_EVEN);

            previewItems.add(ReceiptPreviewResponse.ReceiptPreviewItem.builder()
                    .productName(item.getProductName())
                    .quantity(qty)
                    .unitPrice(unit)
                    .lineAmount(lineAmt)
                    .suggestedCategoryId(suggested.getId())
                    .suggestedCategoryName(suggested.getName())
                    .build());
        }

        BigDecimal detectedTotal = parsed.getDetectedTotalAmount() != null
                ? parsed.getDetectedTotalAmount().setScale(4, RoundingMode.HALF_EVEN)
                : computedSum;

        return ReceiptPreviewResponse.builder()
                .merchantName(parsed.getMerchantName() != null ? parsed.getMerchantName() : "Hóa đơn mua sắm")
                .detectedTotalAmount(detectedTotal)
                .computedSum(computedSum)
                .items(previewItems)
                .build();
    }

    @Transactional
    public PersonalTransactionDto confirmReceipt(UUID userId, ConfirmReceiptRequest request) {
        Account wallet = requireOwnedAccount(userId, request.getWalletId());
        if (!isWalletAccount(wallet)) {
            throw new IllegalArgumentException("Ví thanh toán không hợp lệ.");
        }

        Map<UUID, Account> accountMap = loadUserAccountMap(userId);
        List<PostingLegRequest> legs = new ArrayList<>();
        BigDecimal sumItems = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);
        UUID primaryCategoryId = null;

        for (ConfirmReceiptRequest.ConfirmReceiptItem item : request.getItems()) {
            Account category = accountMap.get(item.getCategoryId());
            if (category == null || category.getAccountType() != AccountType.EXPENSE) {
                throw new IllegalArgumentException("Danh mục chi tiêu không hợp lệ cho mặt hàng: " + item.getProductName());
            }
            BigDecimal itemAmt = item.getLineAmount().setScale(4, RoundingMode.HALF_EVEN);
            if (itemAmt.compareTo(BigDecimal.ZERO) <= 0) continue;

            if (primaryCategoryId == null) {
                primaryCategoryId = category.getId();
            }

            legs.add(PostingLegRequest.builder()
                    .accountId(category.getId())
                    .amount(itemAmt)
                    .build());
            sumItems = sumItems.add(itemAmt).setScale(4, RoundingMode.HALF_EVEN);

            // Train active categorizer with user's confirmed choice
            localTrainableCategorizer.train(item.getProductName(), category.getId());
        }

        if (legs.isEmpty()) {
            throw new IllegalArgumentException("Tổng tiền hóa đơn phải lớn hơn 0.");
        }

        BigDecimal paid = request.getActualPaidAmount() != null && request.getActualPaidAmount().compareTo(BigDecimal.ZERO) > 0
                ? request.getActualPaidAmount().setScale(4, RoundingMode.HALF_EVEN)
                : sumItems;

        BigDecimal diff = paid.subtract(sumItems).setScale(4, RoundingMode.HALF_EVEN);
        if (diff.compareTo(BigDecimal.ZERO) != 0) {
            legs.add(PostingLegRequest.builder()
                    .accountId(primaryCategoryId)
                    .amount(diff)
                    .build());
        }

        // Wallet Credit leg (-paid)
        legs.add(PostingLegRequest.builder()
                .accountId(wallet.getId())
                .amount(paid.negate())
                .build());

        String merchant = request.getMerchantName() != null && !request.getMerchantName().isBlank()
                ? request.getMerchantName().trim()
                : "Hóa đơn mua sắm";
        String desc = merchant + " (" + request.getItems().size() + " món)";

        JournalEntryResponse recorded = ledgerService.recordTransaction(RecordTransactionRequest.builder()
                .userId(userId)
                .entryType("RECEIPT_SCAN")
                .walletAccountId(wallet.getId())
                .categoryAccountId(primaryCategoryId)
                .transactionDate(request.getTransactionDate() != null ? request.getTransactionDate() : Instant.now())
                .description(desc)
                .referenceId("RECEIPT:" + UUID.randomUUID())
                .postingLegs(legs)
                .build());

        JournalEntry entity = journalEntryRepository.findById(recorded.getId()).orElseThrow();
        return mapToPersonalTransactionDto(entity, accountMap, Collections.emptySet());
    }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    private boolean isWalletAccount(Account acc) {
        if (acc.isArchived()) return false;
        if (acc.getAccountSubtype() != null) {
            return WALLET_SUBTYPES.contains(acc.getAccountSubtype());
        }
        return acc.getAccountType() == AccountType.ASSET || acc.getAccountType() == AccountType.LIABILITY;
    }

    private boolean isCategoryAccount(Account acc) {
        if (acc.isArchived()) return false;
        return acc.getAccountType() == AccountType.EXPENSE || acc.getAccountType() == AccountType.REVENUE;
    }

    private boolean isEntryCancelledOrReversal(JournalEntry entry, Set<UUID> reversedIds) {
        return entry.getReversalOfId() != null || reversedIds.contains(entry.getId());
    }

    private Account requireOwnedAccount(UUID userId, UUID accountId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private Map<UUID, Account> loadUserAccountMap(UUID userId) {
        return accountRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
    }

    private Account getOrCreateSystemEquityAccount(UUID userId) {
        return accountRepository.findFirstByUserIdAndAccountSubtype(userId, "SYSTEM_EQUITY")
                .orElseGet(() -> {
                    AccountResponse created = ledgerService.createAccount(CreateAccountRequest.builder()
                            .userId(userId)
                            .name("Vốn đối ứng số dư")
                            .accountType(AccountType.EQUITY)
                            .accountSubtype("SYSTEM_EQUITY")
                            .currency("VND")
                            .initialBalance(BigDecimal.ZERO)
                            .allowNegative(true)
                            .includeInTotal(false)
                            .icon("shield")
                            .color("#64748b")
                            .build());
                    return accountRepository.findById(created.getId()).orElseThrow();
                });
    }

    private void syncBankAccountMappingIfPresent(UUID userId, UUID walletId, String bankCode, String accountNumber) {
        String cleanAccNum = blankToNull(accountNumber);
        if (cleanAccNum == null) {
            return;
        }
        List<Account> userAccounts = accountRepository.findByUserIdAndArchivedFalseOrderByCreatedAtAsc(userId);
        UUID defaultExpenseId = userAccounts.stream()
                .filter(a -> a.getAccountType() == AccountType.EXPENSE)
                .reduce((first, second) -> second) // last is "Chi phí khác"
                .map(Account::getId)
                .orElse(null);
        UUID defaultRevenueId = userAccounts.stream()
                .filter(a -> a.getAccountType() == AccountType.REVENUE)
                .reduce((first, second) -> second) // last is "Thu nhập khác"
                .map(Account::getId)
                .orElse(null);

        if (defaultExpenseId == null || defaultRevenueId == null) {
            return;
        }

        Optional<BankAccountMapping> byLedger = bankAccountMappingRepository.findByLedgerAccountId(walletId);
        Optional<BankAccountMapping> byNumber = bankAccountMappingRepository.findByBankAccountNumber(cleanAccNum);

        if (byNumber.isPresent() && !byNumber.get().getLedgerAccountId().equals(walletId)) {
            // Account number already mapped elsewhere — skip silently unless owned by same user
            if (!userId.equals(byNumber.get().getUserId())) {
                return;
            }
            BankAccountMapping m = byNumber.get();
            m.setLedgerAccountId(walletId);
            m.setBankName(bankCode != null && !bankCode.isBlank() ? bankCode : "Bank");
            bankAccountMappingRepository.save(m);
            return;
        }

        BankAccountMapping mapping = byLedger.orElseGet(() -> BankAccountMapping.builder()
                .userId(userId)
                .ledgerAccountId(walletId)
                .defaultSuspenseExpenseAccountId(defaultExpenseId)
                .defaultSuspenseRevenueAccountId(defaultRevenueId)
                .build());
        mapping.setBankAccountNumber(cleanAccNum);
        mapping.setBankName(bankCode != null && !bankCode.isBlank() ? bankCode : "Bank");
        bankAccountMappingRepository.save(mapping);
    }

    private PersonalTransactionDto mapToPersonalTransactionDto(
            JournalEntry entry,
            Map<UUID, Account> accountMap,
            Set<UUID> reversedIds) {

        boolean isCancelled = reversedIds.contains(entry.getId());
        String type = entry.getEntryType();

        Account wallet = entry.getWalletAccountId() != null ? accountMap.get(entry.getWalletAccountId()) : null;
        Account counterparty = entry.getCategoryAccountId() != null ? accountMap.get(entry.getCategoryAccountId()) : null;

        // Fallback inference for webhook or legacy entries if metadata was null
        if (wallet == null || type == null) {
            for (PostingLeg leg : entry.getPostingLegs()) {
                Account acc = accountMap.get(leg.getAccount().getId());
                if (acc == null) continue;
                if (wallet == null && (acc.getAccountType() == AccountType.ASSET || acc.getAccountType() == AccountType.LIABILITY)
                        && !"GROUP_DEBT".equals(acc.getAccountSubtype())) {
                    wallet = acc;
                } else if (counterparty == null) {
                    counterparty = acc;
                }
            }
            if (type == null && counterparty != null) {
                if (counterparty.getAccountType() == AccountType.EXPENSE) type = "EXPENSE";
                else if (counterparty.getAccountType() == AccountType.REVENUE) type = "INCOME";
                else if (counterparty.getAccountType() == AccountType.EQUITY) type = "ADJUSTMENT";
                else type = "TRANSFER";
            }
        }

        if (wallet == null) {
            return null;
        }

        // Determine transaction display amount from wallet leg
        BigDecimal walletDelta = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);
        List<PersonalTransactionDto.SplitItemBreakdown> breakdown = new ArrayList<>();

        for (PostingLeg leg : entry.getPostingLegs()) {
            UUID legAccId = leg.getAccount().getId();
            if (legAccId.equals(wallet.getId())) {
                walletDelta = walletDelta.add(leg.getAmount()).setScale(4, RoundingMode.HALF_EVEN);
            } else {
                Account legAcc = accountMap.get(legAccId);
                if (legAcc != null && legAcc.getAccountType() == AccountType.EXPENSE && leg.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                    breakdown.add(PersonalTransactionDto.SplitItemBreakdown.builder()
                            .categoryId(legAcc.getId())
                            .categoryName(legAcc.getName())
                            .categoryColor(legAcc.getColor())
                            .amount(leg.getAmount().setScale(4, RoundingMode.HALF_EVEN))
                            .build());
                }
            }
        }

        BigDecimal displayAmount = walletDelta.abs().setScale(4, RoundingMode.HALF_EVEN);
        if ("ADJUSTMENT".equals(type) || "OPENING_BALANCE".equals(type)) {
            displayAmount = walletDelta; // Keep sign for adjustments/opening balance
        }

        PersonalTransactionDto.PersonalTransactionDtoBuilder builder = PersonalTransactionDto.builder()
                .id(entry.getId())
                .type(type != null ? type : "EXPENSE")
                .amount(displayAmount)
                .description(entry.getDescription())
                .transactionDate(entry.getTransactionDate())
                .createdAt(entry.getCreatedAt())
                .walletId(wallet.getId())
                .walletName(wallet.getName())
                .walletIcon(wallet.getIcon() != null ? wallet.getIcon() : "wallet")
                .walletColor(wallet.getColor() != null ? wallet.getColor() : "#22c55e")
                .cancelled(isCancelled)
                .reversalOfId(entry.getReversalOfId())
                .breakdown(breakdown.size() > 1 ? breakdown : Collections.emptyList());

        if ("TRANSFER".equals(type) && counterparty != null) {
            builder.toWalletId(counterparty.getId())
                    .toWalletName(counterparty.getName())
                    .toWalletIcon(counterparty.getIcon() != null ? counterparty.getIcon() : "wallet")
                    .toWalletColor(counterparty.getColor() != null ? counterparty.getColor() : "#38bdf8");
        } else if (counterparty != null) {
            builder.categoryId(counterparty.getId())
                    .categoryName(counterparty.getName())
                    .categoryIcon(counterparty.getIcon() != null ? counterparty.getIcon() : "tag")
                    .categoryColor(counterparty.getColor() != null ? counterparty.getColor() : "#ef4444");
        }

        return builder.build();
    }

    private List<TopSpendingItemDto> buildCategoryBreakdownList(
            Map<UUID, BigDecimal> categoryAmounts,
            Map<UUID, Account> accountMap) {

        BigDecimal total = categoryAmounts.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_EVEN);

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return Collections.emptyList();
        }

        return categoryAmounts.entrySet().stream()
                .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(entry -> {
                    Account cat = accountMap.get(entry.getKey());
                    BigDecimal amt = entry.getValue().setScale(4, RoundingMode.HALF_EVEN);
                    BigDecimal pct = amt.multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_EVEN);
                    return TopSpendingItemDto.builder()
                            .categoryId(entry.getKey())
                            .categoryName(cat != null ? cat.getName() : "Khác")
                            .icon(cat != null && cat.getIcon() != null ? cat.getIcon() : "tag")
                            .color(cat != null && cat.getColor() != null ? cat.getColor() : "#ef4444")
                            .amount(amt)
                            .percentage(pct)
                            .build();
                })
                .toList();
    }

    private Account matchUserExpenseCategory(String productName, List<Account> expenseCategories) {
        String norm = stripAccents(productName != null ? productName.toLowerCase() : "");
        String targetKeyword = "sieu thi";

        if (norm.contains("thuoc") || norm.contains("vitamin") || norm.contains("khau trang") || norm.contains("y te")) {
            targetKeyword = "suc khoe";
        } else if (norm.contains("nuoc rua") || norm.contains("giay") || norm.contains("bot giat") || norm.contains("xa phong")) {
            targetKeyword = "sieu thi";
        } else if (norm.contains("ca phe") || norm.contains("tra sua") || norm.contains("bun") || norm.contains("pho") || norm.contains("com")) {
            targetKeyword = "an uong";
        } else if (norm.contains("thit") || norm.contains("ca ") || norm.contains("rau") || norm.contains("sua") || norm.contains("mi ") || norm.contains("dau an") || norm.contains("tuong ot")) {
            targetKeyword = "sieu thi";
        }

        final String kw = targetKeyword;
        for (Account cat : expenseCategories) {
            if (stripAccents(cat.getName().toLowerCase()).contains(kw)) {
                return cat;
            }
        }
        return expenseCategories.get(0);
    }

    private WalletDto mapToWalletDto(Account account) {
        return WalletDto.builder()
                .id(account.getId())
                .name(account.getName())
                .subtype(account.getAccountSubtype() != null ? account.getAccountSubtype() : "CASH")
                .currency(account.getCurrency())
                .balance(account.getCachedBalance().setScale(4, RoundingMode.HALF_EVEN))
                .icon(account.getIcon() != null ? account.getIcon() : "wallet")
                .color(account.getColor() != null ? account.getColor() : "#22c55e")
                .bankCode(account.getBankCode())
                .accountNumber(account.getAccountNumber())
                .includeInTotal(account.isIncludeInTotal())
                .allowNegative(account.isAllowNegative())
                .createdAt(account.getCreatedAt())
                .build();
    }

    private CategoryDto mapToCategoryDto(Account account) {
        boolean isIncome = account.getAccountType() == AccountType.REVENUE;
        return CategoryDto.builder()
                .id(account.getId())
                .name(account.getName())
                .type(isIncome ? "INCOME" : "EXPENSE")
                .icon(account.getIcon() != null ? account.getIcon() : "tag")
                .color(account.getColor() != null ? account.getColor() : (isIncome ? "#22c55e" : "#ef4444"))
                .build();
    }

    private String normalizeWalletSubtype(String raw) {
        if (raw == null || raw.isBlank()) return "CASH";
        String upper = raw.trim().toUpperCase();
        return WALLET_SUBTYPES.contains(upper) ? upper : "CASH";
    }

    private YearMonth parseYearMonthOrCurrent(String month) {
        if (month != null && !month.isBlank()) {
            try {
                return YearMonth.parse(month.trim(), YEAR_MONTH_FMT);
            } catch (Exception ignored) {
            }
        }
        return YearMonth.now(VN_ZONE);
    }

    private BigDecimal[] initZeroArray(int size) {
        BigDecimal[] arr = new BigDecimal[size];
        Arrays.fill(arr, BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN));
        return arr;
    }

    private String blankToNull(String val) {
        return (val == null || val.isBlank()) ? null : val.trim();
    }

    private String stripAccents(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D');
    }
}

