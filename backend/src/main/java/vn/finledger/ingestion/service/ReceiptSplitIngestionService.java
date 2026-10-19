package vn.finledger.ingestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.finledger.ingestion.domain.BankAccountMapping;
import vn.finledger.ingestion.dto.ReceiptIngestionResponse;
import vn.finledger.ingestion.ocr.ParsedReceiptResult;
import vn.finledger.ingestion.ocr.ReceiptLineItemDto;
import vn.finledger.ingestion.repository.BankAccountMappingRepository;
import vn.finledger.ingestion.security.PiiSanitizer;
import vn.finledger.ledger.dto.JournalEntryResponse;
import vn.finledger.ledger.dto.PostingLegRequest;
import vn.finledger.ledger.dto.RecordTransactionRequest;
import vn.finledger.ledger.service.LedgerService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service orchestrating receipt splitting, categorization, and double-entry ledger persistence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptSplitIngestionService {

    private final BankAccountMappingRepository bankAccountMappingRepository;
    private final LocalTrainableCategorizer localTrainableCategorizer;
    private final LedgerService ledgerService;
    private final PiiSanitizer piiSanitizer;

    /**
     * Ingests parsed supermarket receipt items into double-entry ledger.
     * Categorizes each line item, validates balancing invariant, and persists immutable journal entry.
     */
    @Transactional
    public ReceiptIngestionResponse ingestParsedReceipt(
            String bankAccountNumber,
            ParsedReceiptResult parsedReceipt,
            BigDecimal actualPaidAmount) {

        if (bankAccountNumber == null || bankAccountNumber.isBlank()) {
            throw new IllegalArgumentException("Bank account number is required");
        }

        if (parsedReceipt == null || parsedReceipt.getLineItems() == null || parsedReceipt.getLineItems().isEmpty()) {
            throw new IllegalArgumentException("No receipt line items found to ingest");
        }

        // 1. Lookup Bank Account Mapping
        BankAccountMapping mapping = bankAccountMappingRepository.findByBankAccountNumber(bankAccountNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No ledger mapping found for bank account: " + bankAccountNumber));

        List<PostingLegRequest> postingLegs = new ArrayList<>();
        BigDecimal sumItems = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);

        // 2. Build Debit Posting Legs for each line item (Expense accounts)
        for (ReceiptLineItemDto item : parsedReceipt.getLineItems()) {
            BigDecimal itemAmount = item.getLineAmount().setScale(4, RoundingMode.HALF_EVEN);
            if (itemAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            CategorizationResult catResult = localTrainableCategorizer.categorizeItem(
                    item.getProductName(), mapping);

            postingLegs.add(PostingLegRequest.builder()
                    .accountId(catResult.getTargetAccountId())
                    .amount(itemAmount) // Positive for Expense Debit
                    .build());

            sumItems = sumItems.add(itemAmount);
        }

        if (postingLegs.isEmpty()) {
            throw new IllegalArgumentException("Total line items amount must be greater than zero");
        }

        // 3. Determine Bank Payment Credit Leg
        BigDecimal bankPaid = actualPaidAmount != null
                ? actualPaidAmount.setScale(4, RoundingMode.HALF_EVEN)
                : (parsedReceipt.getDetectedTotalAmount() != null
                ? parsedReceipt.getDetectedTotalAmount().setScale(4, RoundingMode.HALF_EVEN)
                : sumItems);

        // 4. Handle rounding / discount difference to guarantee double-entry balance: SUM = 0.0000
        BigDecimal discrepancy = bankPaid.subtract(sumItems).setScale(4, RoundingMode.HALF_EVEN);
        if (discrepancy.compareTo(BigDecimal.ZERO) != 0) {
            log.info("Discrepancy detected between bank paid ({}) and items sum ({}): diff={}. Adding balancing leg.",
                    bankPaid, sumItems, discrepancy);
            postingLegs.add(PostingLegRequest.builder()
                    .accountId(mapping.getDefaultSuspenseExpenseAccountId())
                    .amount(discrepancy)
                    .build());
        }

        // Add Bank Credit Leg (-bankPaid)
        postingLegs.add(PostingLegRequest.builder()
                .accountId(mapping.getLedgerAccountId())
                .amount(bankPaid.negate()) // Negative for Asset Credit
                .build());

        // 5. Construct and validate RecordTransactionRequest
        String merchant = parsedReceipt.getMerchantName() != null ? parsedReceipt.getMerchantName() : "Supermarket";
        String description = "Receipt: " + piiSanitizer.sanitize(merchant)
                + " (" + parsedReceipt.getLineItems().size() + " items)";
        String referenceId = "RECEIPT:" + UUID.randomUUID();

        RecordTransactionRequest recordRequest = RecordTransactionRequest.builder()
                .transactionDate(Instant.now())
                .description(description)
                .referenceId(referenceId)
                .postingLegs(postingLegs)
                .build();

        JournalEntryResponse entryResponse = ledgerService.recordTransaction(recordRequest);
        log.info("Successfully recorded receipt journal entry: {} with {} legs",
                entryResponse.getId(), postingLegs.size());

        return ReceiptIngestionResponse.builder()
                .journalEntryId(entryResponse.getId())
                .status("PROCESSED")
                .merchantName(merchant)
                .totalPaidAmount(bankPaid)
                .items(parsedReceipt.getLineItems())
                .legCount(postingLegs.size())
                .message("Receipt processed and recorded into double-entry ledger successfully")
                .build();
    }
}
