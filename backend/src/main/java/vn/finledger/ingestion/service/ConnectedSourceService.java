package vn.finledger.ingestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.finledger.finance.dto.CreateWalletRequest;
import vn.finledger.finance.dto.UpdateWalletRequest;
import vn.finledger.finance.dto.WalletDto;
import vn.finledger.finance.service.PersonalFinanceService;
import vn.finledger.ingestion.domain.BankAccountMapping;
import vn.finledger.ingestion.domain.IngestionFingerprint;
import vn.finledger.ingestion.dto.ConnectFinancialSourceRequest;
import vn.finledger.ingestion.dto.ConnectedSourceTelemetryDto;
import vn.finledger.ingestion.dto.SyncedEventLogDto;
import vn.finledger.ingestion.repository.BankAccountMappingRepository;
import vn.finledger.ingestion.repository.IngestionFingerprintRepository;
import vn.finledger.ledger.domain.Account;
import vn.finledger.ledger.domain.JournalEntry;
import vn.finledger.ledger.repository.AccountRepository;
import vn.finledger.ledger.repository.JournalEntryRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectedSourceService {

    private final BankAccountMappingRepository mappingRepository;
    private final IngestionFingerprintRepository fingerprintRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final PersonalFinanceService personalFinanceService;

    @Transactional(readOnly = true)
    public List<SyncedEventLogDto> listUserSyncedEvents(UUID userId) {
        List<BankAccountMapping> mappings = mappingRepository.findByUserId(userId);
        if (mappings.isEmpty()) {
            return Collections.emptyList();
        }
        Map<UUID, BankAccountMapping> mapByLedgerId = mappings.stream()
                .collect(Collectors.toMap(BankAccountMapping::getLedgerAccountId, Function.identity(), (a, b) -> a));
        Map<UUID, Account> accountMap = accountRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        List<IngestionFingerprint> fps = fingerprintRepository
                .findTop25BySourceAccountIdInOrderByCreatedAtDesc(mapByLedgerId.keySet());
        if (fps.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> jeIds = fps.stream().map(IngestionFingerprint::getJournalEntryId).collect(Collectors.toSet());
        Map<UUID, JournalEntry> jeMap = journalEntryRepository.findAllById(jeIds).stream()
                .collect(Collectors.toMap(JournalEntry::getId, Function.identity()));

        return fps.stream()
                .map(fp -> {
                    BankAccountMapping m = mapByLedgerId.get(fp.getSourceAccountId());
                    Account wallet = accountMap.get(fp.getSourceAccountId());
                    JournalEntry je = jeMap.get(fp.getJournalEntryId());
                    return SyncedEventLogDto.builder()
                            .id(fp.getId())
                            .providerCode(m != null ? m.getProviderCode() : "BANK")
                            .walletName(wallet != null ? wallet.getName() : (m != null ? m.getBankName() : "Ví"))
                            .transferDirection(fp.getTransferDirection())
                            .entryType(je != null ? je.getEntryType() : "EXPENSE")
                            .amount(fp.getExactAmount())
                            .description(je != null ? je.getDescription() : "")
                            .extractedReferenceCode(fp.getNormalizedRefCode())
                            .sourcesMerged(fp.getSourcesMerged())
                            .journalEntryId(fp.getJournalEntryId())
                            .createdAt(fp.getCreatedAt())
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConnectedSourceTelemetryDto> listUserSources(UUID userId) {
        List<BankAccountMapping> mappings = mappingRepository.findByUserId(userId);
        if (mappings.isEmpty()) {
            return Collections.emptyList();
        }
        Map<UUID, Account> accountMap = accountRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        return mappings.stream()
                .sorted(Comparator.comparing(BankAccountMapping::getCreatedAt))
                .map(m -> toTelemetryDto(m, accountMap.get(m.getLedgerAccountId())))
                .toList();
    }

    @Transactional
    public ConnectedSourceTelemetryDto connectSource(UUID userId, ConnectFinancialSourceRequest request) {
        String providerCode = normalizeProvider(request.getProviderCode());
        String identifier = request.getAccountIdentifier().trim();

        // Reject hijack of another user's existing mapping
        Optional<BankAccountMapping> existingGlobal = mappingRepository.findByBankAccountNumber(identifier);
        if (existingGlobal.isPresent() && existingGlobal.get().getUserId() != null
                && !existingGlobal.get().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Số tài khoản hoặc số ví này đã được liên kết bởi tài khoản khác.");
        }

        UUID targetWalletId = request.getWalletId();
        if (targetWalletId != null) {
            Account wallet = accountRepository.findByIdAndUserId(targetWalletId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ví thuộc sở hữu của bạn."));
            personalFinanceService.updateWallet(userId, targetWalletId, UpdateWalletRequest.builder()
                    .name(request.getWalletName() != null && !request.getWalletName().isBlank()
                            ? request.getWalletName().trim()
                            : wallet.getName())
                    .bankCode(providerCode)
                    .accountNumber(identifier)
                    .targetBalance(request.getInitialBalance())
                    .build());
        } else {
            Optional<BankAccountMapping> existingForProvider = mappingRepository
                    .findFirstByUserIdAndProviderCodeOrderByCreatedAtAsc(userId, providerCode);
            if (existingForProvider.isPresent()) {
                targetWalletId = existingForProvider.get().getLedgerAccountId();
                Account wallet = accountRepository.findByIdAndUserId(targetWalletId, userId)
                        .orElseThrow(() -> new IllegalStateException("Ví liên kết không tồn tại."));
                personalFinanceService.updateWallet(userId, targetWalletId, UpdateWalletRequest.builder()
                        .name(request.getWalletName() != null && !request.getWalletName().isBlank()
                                ? request.getWalletName().trim()
                                : wallet.getName())
                        .bankCode(providerCode)
                        .accountNumber(identifier)
                        .targetBalance(request.getInitialBalance())
                        .build());
            } else {
                boolean isMoMo = "MOMO".equals(providerCode);
                String defaultName = isMoMo ? "Ví MoMo (" + identifier + ")" : "Vietcombank (" + identifier + ")";
                WalletDto created = personalFinanceService.createWallet(userId, CreateWalletRequest.builder()
                        .name(request.getWalletName() != null && !request.getWalletName().isBlank()
                                ? request.getWalletName().trim()
                                : defaultName)
                        .subtype(isMoMo ? "EWALLET" : "BANK")
                        .bankCode(providerCode)
                        .accountNumber(identifier)
                        .initialBalance(request.getInitialBalance() != null ? request.getInitialBalance() : BigDecimal.ZERO)
                        .icon(isMoMo ? "smartphone" : "landmark")
                        .color(isMoMo ? "#ec4899" : "#10b981")
                        .includeInTotal(true)
                        .build());
                targetWalletId = created.getId();
            }
        }

        BankAccountMapping saved = mappingRepository.findByLedgerAccountId(targetWalletId)
                .orElseThrow(() -> new IllegalStateException("Không thể đồng bộ cấu hình nguồn tiền."));
        saved.setProviderCode(providerCode);
        saved.setAccountIdentifier(identifier);
        saved.setBankName(providerCode);
        saved = mappingRepository.save(saved);

        Account wallet = accountRepository.findById(targetWalletId).orElseThrow();
        return toTelemetryDto(saved, wallet);
    }

    @Transactional
    public List<ConnectedSourceTelemetryDto> rotateUserBridgeToken(UUID userId) {
        List<BankAccountMapping> mappings = mappingRepository.findByUserId(userId);
        if (mappings.isEmpty()) {
            return Collections.emptyList();
        }
        String newToken = "flb_" + UUID.randomUUID().toString().replace("-", "");
        for (BankAccountMapping m : mappings) {
            m.setWebhookSecretToken(newToken);
            mappingRepository.save(m);
        }
        return listUserSources(userId);
    }

    private ConnectedSourceTelemetryDto toTelemetryDto(BankAccountMapping m, Account wallet) {
        BigDecimal ledgerBal = wallet != null && wallet.getCachedBalance() != null
                ? wallet.getCachedBalance().setScale(4, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal reported = m.getLastReportedBalance() != null
                ? m.getLastReportedBalance().setScale(4, RoundingMode.HALF_EVEN)
                : null;
        BigDecimal delta = reported != null
                ? ledgerBal.subtract(reported).setScale(4, RoundingMode.HALF_EVEN)
                : null;
        boolean reconciled = delta != null && delta.compareTo(BigDecimal.ZERO) == 0;

        return ConnectedSourceTelemetryDto.builder()
                .id(m.getId())
                .providerCode(m.getProviderCode())
                .bankName(m.getBankName())
                .accountIdentifier(m.getAccountIdentifier())
                .walletId(m.getLedgerAccountId())
                .walletName(wallet != null ? wallet.getName() : m.getBankName())
                .ledgerBalance(ledgerBal)
                .lastReportedBalance(reported)
                .reconciliationDelta(delta)
                .reconciled(reconciled)
                .lastSyncedAt(m.getLastSyncedAt())
                .webhookSecretToken(m.getWebhookSecretToken())
                .build();
    }

    private String normalizeProvider(String raw) {
        String norm = raw.trim().toUpperCase(Locale.ROOT);
        if (norm.contains("VCB") || norm.contains("VIETCOMBANK")) return "VCB";
        if (norm.contains("MOMO")) return "MOMO";
        throw new IllegalArgumentException("Hiện chỉ hỗ trợ kết nối Vietcombank (VCB) hoặc Ví MoMo (MOMO).");
    }
}

