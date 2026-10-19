package vn.finledger.ledger.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.finledger.ledger.domain.Account;
import vn.finledger.ledger.domain.JournalEntry;
import vn.finledger.ledger.domain.PostingLeg;
import vn.finledger.ledger.dto.*;
import vn.finledger.ledger.exception.AccountNotFoundException;
import vn.finledger.ledger.exception.DuplicateTransactionException;
import vn.finledger.ledger.exception.InsufficientFundsException;
import vn.finledger.ledger.exception.TransactionNotFoundException;
import vn.finledger.ledger.exception.UnbalancedTransactionException;
import vn.finledger.ledger.repository.AccountRepository;
import vn.finledger.ledger.repository.JournalEntryRepository;
import vn.finledger.ledger.repository.PostingLegRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final PostingLegRepository postingLegRepository;

    @Transactional
    public JournalEntryResponse recordTransaction(RecordTransactionRequest request) {
        // 1. Double-Entry Invariant Validation: At least 2 legs
        if (request.getPostingLegs() == null || request.getPostingLegs().size() < 2) {
            throw new UnbalancedTransactionException("A double-entry transaction must contain at least two posting legs.");
        }

        // 2. Validate non-zero amount and sum to exact zero
        BigDecimal sum = BigDecimal.ZERO;
        for (PostingLegRequest leg : request.getPostingLegs()) {
            if (leg.getAmount() == null || leg.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                throw new UnbalancedTransactionException("Posting leg amount cannot be zero.");
            }
            sum = sum.add(leg.getAmount());
        }

        if (sum.compareTo(BigDecimal.ZERO) != 0) {
            throw new UnbalancedTransactionException(
                    "Transaction is unbalanced. Sum of posting legs must equal 0.0000, but was " + sum);
        }

        // 3. Idempotency Check: if referenceId is provided and exists, return existing transaction
        if (request.getReferenceId() != null && !request.getReferenceId().isBlank()) {
            Optional<JournalEntry> existing = journalEntryRepository.findByReferenceId(request.getReferenceId());
            if (existing.isPresent()) {
                log.info("Idempotent hit for referenceId: {}", request.getReferenceId());
                return mapToJournalEntryResponse(existing.get());
            }
        }

        // 4. Deadlock Prevention: Sort distinct account IDs in ascending order and acquire Pessimistic Write Lock
        List<UUID> sortedAccountIds = request.getPostingLegs().stream()
                .map(PostingLegRequest::getAccountId)
                .distinct()
                .sorted()
                .toList();

        List<Account> lockedAccounts = accountRepository.findAllByIdInOrderByIdAscForUpdate(sortedAccountIds);
        if (lockedAccounts.size() != sortedAccountIds.size()) {
            throw new AccountNotFoundException("One or more accounts in the transaction legs do not exist.");
        }

        Map<UUID, Account> accountMap = lockedAccounts.stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        // 5. Aggregate net deltas and validate account balance invariants
        Map<UUID, BigDecimal> netDeltas = new HashMap<>();
        for (PostingLegRequest leg : request.getPostingLegs()) {
            netDeltas.merge(leg.getAccountId(), leg.getAmount(), BigDecimal::add);
        }

        for (Map.Entry<UUID, BigDecimal> entry : netDeltas.entrySet()) {
            Account account = accountMap.get(entry.getKey());
            BigDecimal delta = entry.getValue();
            BigDecimal newBalance = account.getCachedBalance().add(delta).setScale(4, RoundingMode.HALF_EVEN);

            if (!account.isAllowNegative() && newBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new InsufficientFundsException(
                        String.format("Account %s (%s) has insufficient funds. Current: %s, delta: %s, resulting: %s",
                                account.getId(), account.getName(), account.getCachedBalance(), delta, newBalance));
            }

            account.setCachedBalance(newBalance);
            accountRepository.save(account);
        }

        // 6. Persist immutable JournalEntry and PostingLegs
        JournalEntry journalEntry = JournalEntry.builder()
                .transactionDate(request.getTransactionDate() != null ? request.getTransactionDate() : Instant.now())
                .description(request.getDescription())
                .referenceId(request.getReferenceId())
                .build();

        for (PostingLegRequest legRequest : request.getPostingLegs()) {
            Account account = accountMap.get(legRequest.getAccountId());
            PostingLeg leg = PostingLeg.builder()
                    .account(account)
                    .amount(legRequest.getAmount().setScale(4, RoundingMode.HALF_EVEN))
                    .build();
            journalEntry.addPostingLeg(leg);
        }

        JournalEntry savedEntry = journalEntryRepository.save(journalEntry);
        log.info("Successfully recorded journal entry: {} with {} legs", savedEntry.getId(), savedEntry.getPostingLegs().size());

        return mapToJournalEntryResponse(savedEntry);
    }

    @Transactional
    public JournalEntryResponse reverseTransaction(UUID journalEntryId, String reason) {
        JournalEntry original = journalEntryRepository.findById(journalEntryId)
                .orElseThrow(() -> new TransactionNotFoundException(journalEntryId));

        if (original.getReversalOfId() != null) {
            throw new IllegalStateException("Cannot reverse a reversal transaction.");
        }

        if (journalEntryRepository.existsByReversalOfId(journalEntryId)) {
            throw new DuplicateTransactionException("Transaction " + journalEntryId + " has already been reversed.");
        }

        // Deadlock Prevention: Lock affected accounts in ascending ID order
        List<UUID> sortedAccountIds = original.getPostingLegs().stream()
                .map(leg -> leg.getAccount().getId())
                .distinct()
                .sorted()
                .toList();

        List<Account> lockedAccounts = accountRepository.findAllByIdInOrderByIdAscForUpdate(sortedAccountIds);
        Map<UUID, Account> accountMap = lockedAccounts.stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        // Invert amounts and update cached balances
        Map<UUID, BigDecimal> reversalDeltas = new HashMap<>();
        for (PostingLeg origLeg : original.getPostingLegs()) {
            reversalDeltas.merge(origLeg.getAccount().getId(), origLeg.getAmount().negate(), BigDecimal::add);
        }

        for (Map.Entry<UUID, BigDecimal> entry : reversalDeltas.entrySet()) {
            Account account = accountMap.get(entry.getKey());
            BigDecimal delta = entry.getValue();
            BigDecimal newBalance = account.getCachedBalance().add(delta).setScale(4, RoundingMode.HALF_EVEN);

            if (!account.isAllowNegative() && newBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new InsufficientFundsException(
                        String.format("Account %s (%s) has insufficient funds to process reversal. Current: %s, delta: %s",
                                account.getId(), account.getName(), account.getCachedBalance(), delta));
            }

            account.setCachedBalance(newBalance);
            accountRepository.save(account);
        }

        // Persist reversing JournalEntry pointing to original via reversal_of_id
        JournalEntry reversalEntry = JournalEntry.builder()
                .transactionDate(Instant.now())
                .description("REVERSAL: " + original.getDescription() + (reason != null && !reason.isBlank() ? " (" + reason + ")" : ""))
                .reversalOfId(journalEntryId)
                .build();

        for (PostingLeg origLeg : original.getPostingLegs()) {
            Account account = accountMap.get(origLeg.getAccount().getId());
            PostingLeg revLeg = PostingLeg.builder()
                    .account(account)
                    .amount(origLeg.getAmount().negate().setScale(4, RoundingMode.HALF_EVEN))
                    .build();
            reversalEntry.addPostingLeg(revLeg);
        }

        JournalEntry savedReversal = journalEntryRepository.save(reversalEntry);
        log.info("Successfully created reversing journal entry: {} for original: {}", savedReversal.getId(), journalEntryId);

        return mapToJournalEntryResponse(savedReversal);
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        BigDecimal initialBal = request.getInitialBalance() != null
                ? request.getInitialBalance().setScale(4, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);

        Account account = Account.builder()
                .name(request.getName())
                .accountType(request.getAccountType())
                .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                .cachedBalance(initialBal)
                .allowNegative(request.isAllowNegative())
                .build();

        Account saved = accountRepository.save(account);
        return mapToAccountResponse(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        return mapToAccountResponse(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::mapToAccountResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public JournalEntryResponse getJournalEntry(UUID id) {
        JournalEntry entry = journalEntryRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
        return mapToJournalEntryResponse(entry);
    }

    private JournalEntryResponse mapToJournalEntryResponse(JournalEntry entry) {
        List<PostingLegResponse> legs = entry.getPostingLegs().stream()
                .map(leg -> PostingLegResponse.builder()
                        .id(leg.getId())
                        .accountId(leg.getAccount().getId())
                        .accountName(leg.getAccount().getName())
                        .amount(leg.getAmount())
                        .createdAt(leg.getCreatedAt())
                        .build())
                .toList();

        return JournalEntryResponse.builder()
                .id(entry.getId())
                .transactionDate(entry.getTransactionDate())
                .description(entry.getDescription())
                .referenceId(entry.getReferenceId())
                .reversalOfId(entry.getReversalOfId())
                .createdAt(entry.getCreatedAt())
                .postingLegs(legs)
                .build();
    }

    private AccountResponse mapToAccountResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .name(account.getName())
                .accountType(account.getAccountType())
                .currency(account.getCurrency())
                .cachedBalance(account.getCachedBalance())
                .allowNegative(account.isAllowNegative())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
