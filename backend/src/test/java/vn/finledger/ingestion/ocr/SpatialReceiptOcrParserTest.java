package vn.finledger.ingestion.ocr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.finledger.ingestion.domain.BankAccountMapping;
import vn.finledger.ingestion.domain.CategorizationRule;
import vn.finledger.ingestion.dto.ReceiptIngestionResponse;
import vn.finledger.ingestion.repository.BankAccountMappingRepository;
import vn.finledger.ingestion.repository.CategorizationRuleRepository;
import vn.finledger.ingestion.security.PiiSanitizer;
import vn.finledger.ingestion.service.LocalTrainableCategorizer;
import vn.finledger.ingestion.service.ReceiptSplitIngestionService;
import vn.finledger.ledger.domain.Account;
import vn.finledger.ledger.domain.AccountType;
import vn.finledger.ledger.dto.JournalEntryResponse;
import vn.finledger.ledger.dto.PostingLegRequest;
import vn.finledger.ledger.dto.RecordTransactionRequest;
import vn.finledger.ledger.repository.AccountRepository;
import vn.finledger.ledger.service.LedgerService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SpatialReceiptOcrParserTest {

    private SpatialReceiptLayoutParser spatialParser;
    private VietnameseReceiptLineItemExtractor lineItemExtractor;
    private LocalTrainableCategorizer localCategorizer;
    private ReceiptSplitIngestionService splitIngestionService;

    @Mock
    private BankAccountMappingRepository bankAccountMappingRepository;

    @Mock
    private CategorizationRuleRepository categorizationRuleRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerService ledgerService;

    private final PiiSanitizer piiSanitizer = new PiiSanitizer();

    private UUID bankLedgerAccountId;
    private UUID foodExpenseAccountId;
    private UUID householdExpenseAccountId;
    private UUID suspenseExpenseAccountId;
    private BankAccountMapping mapping;

    @BeforeEach
    void setUp() {
        spatialParser = new SpatialReceiptLayoutParser();
        lineItemExtractor = new VietnameseReceiptLineItemExtractor();
        localCategorizer = new LocalTrainableCategorizer(categorizationRuleRepository, accountRepository);
        splitIngestionService = new ReceiptSplitIngestionService(
                bankAccountMappingRepository,
                localCategorizer,
                ledgerService,
                piiSanitizer
        );

        bankLedgerAccountId = UUID.randomUUID();
        foodExpenseAccountId = UUID.randomUUID();
        householdExpenseAccountId = UUID.randomUUID();
        suspenseExpenseAccountId = UUID.randomUUID();

        mapping = BankAccountMapping.builder()
                .id(UUID.randomUUID())
                .bankAccountNumber("1903666888999")
                .bankName("TCB")
                .ledgerAccountId(bankLedgerAccountId)
                .defaultSuspenseExpenseAccountId(suspenseExpenseAccountId)
                .build();
    }

    @Test
    @DisplayName("Test 1 - Skewed Receipt Y-Clustering: Correctly clusters tilted bounding boxes into horizontal lines")
    void testSkewedReceiptYClustering() {
        // Simulating a receipt photographed at a slight angle where left text and right price
        // drift vertically by 5 to 8 pixels, and OCR returns boxes in scrambled order
        List<OcrTextBox> boxes = new ArrayList<>();

        // Row 1: Item "SUA TUOI VINAMILK 180ML", Qty 2, Price "64.000"
        // yMin/yMax skewed: Box 1 (yCenter=110), Box 2 (yCenter=114), Box 3 (yCenter=117) -> drift of 7px
        OcrTextBox r1b1 = new OcrTextBox("SUA TUOI VINAMILK 180ML", 30, 100, 220, 120, 0.95);
        OcrTextBox r1b2 = new OcrTextBox("2", 240, 104, 255, 124, 0.96);
        OcrTextBox r1b3 = new OcrTextBox("64.000", 290, 107, 360, 127, 0.98);

        // Row 2: Item "BANH MI SANDWICH", Qty 1, Price "25.000"
        // Box 4 (yCenter=155), Box 5 (yCenter=159), Box 6 (yCenter=162) -> drift of 7px
        OcrTextBox r2b1 = new OcrTextBox("BANH MI SANDWICH", 30, 145, 200, 165, 0.94);
        OcrTextBox r2b2 = new OcrTextBox("1", 240, 149, 255, 169, 0.95);
        OcrTextBox r2b3 = new OcrTextBox("25.000", 290, 152, 360, 172, 0.97);

        // Intentionally scramble the input order
        boxes.add(r2b3);
        boxes.add(r1b1);
        boxes.add(r2b1);
        boxes.add(r1b3);
        boxes.add(r2b2);
        boxes.add(r1b2);

        // Run Spatial 2D Parser
        List<ReconstructedReceiptRow> reconstructedRows = spatialParser.parse(boxes);

        // Assertions: 2 coherent rows reconstructed
        assertThat(reconstructedRows).hasSize(2);

        ReconstructedReceiptRow row1 = reconstructedRows.get(0);
        ReconstructedReceiptRow row2 = reconstructedRows.get(1);

        // Assert boxes inside each row are sorted left-to-right by xMin
        assertThat(row1.getBoxes()).containsExactly(r1b1, r1b2, r1b3);
        assertThat(row2.getBoxes()).containsExactly(r2b1, r2b2, r2b3);

        assertThat(row1.getJoinedText()).isEqualTo("SUA TUOI VINAMILK 180ML 2 64.000");
        assertThat(row2.getJoinedText()).isEqualTo("BANH MI SANDWICH 1 25.000");

        // Extract items via lineItemExtractor
        ParsedReceiptResult parsed = lineItemExtractor.extract(reconstructedRows);
        assertThat(parsed.getLineItems()).hasSize(2);

        ReceiptLineItemDto item1 = parsed.getLineItems().get(0);
        assertThat(item1.getProductName()).isEqualTo("SUA TUOI VINAMILK 180ML");
        assertThat(item1.getQuantity()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(item1.getLineAmount()).isEqualByComparingTo(new BigDecimal("64000"));

        ReceiptLineItemDto item2 = parsed.getLineItems().get(1);
        assertThat(item2.getProductName()).isEqualTo("BANH MI SANDWICH");
        assertThat(item2.getQuantity()).isEqualByComparingTo(new BigDecimal("1"));
        assertThat(item2.getLineAmount()).isEqualByComparingTo(new BigDecimal("25000"));

        assertThat(parsed.getCalculatedSumAmount()).isEqualByComparingTo(new BigDecimal("89000"));
    }

    @Test
    @DisplayName("Test 2 - Two-Line Supermarket Receipt & Footer Filtering (WinMart style with OCR 'O' fix)")
    void testTwoLineSupermarketReceiptAndFooterFiltering() {
        // Simulating WinMart receipt with Header, 3 Two-Line items, and Footer with Tiền khách đưa / Tiền trả lại
        List<OcrTextBox> boxes = new ArrayList<>();

        // Header Rows
        boxes.add(new OcrTextBox("WINMART+ LE DUAN", 40, 20, 250, 40, 0.98));
        boxes.add(new OcrTextBox("Dia chi: 123 Le Duan, Da Nang", 40, 50, 320, 70, 0.95));
        boxes.add(new OcrTextBox("HD: 009283 - Thu Ngan: NV01", 40, 80, 280, 100, 0.92));

        // Item 1 (Two-Line wrapped):
        // Line 1: Name "THIT BA CHI HEO KHAY 500G"
        boxes.add(new OcrTextBox("THIT BA CHI HEO KHAY 500G", 40, 120, 310, 140, 0.99));
        // Line 2: Formula "1 x 120.000 120.000" (with OCR confusion: "120.00O")
        boxes.add(new OcrTextBox("1 x 120.00O 120.00O", 50, 150, 320, 170, 0.96));

        // Item 2 (Two-Line wrapped with decimal quantity):
        // Line 1: Name "CA HOI NA UY FILLET"
        boxes.add(new OcrTextBox("CA HOI NA UY FILLET", 40, 190, 260, 210, 0.97));
        // Line 2: Formula "0.52 kg x 200.000 104.000"
        boxes.add(new OcrTextBox("0.52 kg x 200.000 104.000", 50, 220, 330, 240, 0.98));

        // Item 3 (Two-Line wrapped):
        // Line 1: Name "RAU CAI XANH DALAT 300G"
        boxes.add(new OcrTextBox("RAU CAI XANH DALAT 300G", 40, 260, 290, 280, 0.98));
        // Line 2: Formula "2 x 63.000 126.000"
        boxes.add(new OcrTextBox("2 x 63.000 126.000", 50, 290, 310, 310, 0.97));

        // Footer Rows:
        // Total line: 120.000 + 104.000 + 126.000 = 350.000
        boxes.add(new OcrTextBox("TỔNG CỘNG: 350.000", 40, 340, 320, 360, 0.99));
        // Payment info that must be ignored
        boxes.add(new OcrTextBox("TIỀN KHÁCH ĐƯA: 500.000", 40, 380, 320, 400, 0.95));
        boxes.add(new OcrTextBox("TIỀN TRẢ LẠI: 150.000", 40, 410, 320, 430, 0.94));

        // Spatial grouping
        List<ReconstructedReceiptRow> rows = spatialParser.parse(boxes);

        // State Machine extraction
        ParsedReceiptResult parsed = lineItemExtractor.extract(rows);

        // Assertions: Exactly 3 items extracted
        assertThat(parsed.getLineItems()).hasSize(3);

        ReceiptLineItemDto item1 = parsed.getLineItems().get(0);
        assertThat(item1.getProductName()).isEqualTo("THIT BA CHI HEO KHAY 500G");
        assertThat(item1.getQuantity()).isEqualByComparingTo(new BigDecimal("1"));
        assertThat(item1.getUnitPrice()).isEqualByComparingTo(new BigDecimal("120000"));
        assertThat(item1.getLineAmount()).isEqualByComparingTo(new BigDecimal("120000"));

        ReceiptLineItemDto item2 = parsed.getLineItems().get(1);
        assertThat(item2.getProductName()).isEqualTo("CA HOI NA UY FILLET");
        assertThat(item2.getQuantity()).isEqualByComparingTo(new BigDecimal("0.52"));
        assertThat(item2.getUnitPrice()).isEqualByComparingTo(new BigDecimal("200000"));
        assertThat(item2.getLineAmount()).isEqualByComparingTo(new BigDecimal("104000"));

        ReceiptLineItemDto item3 = parsed.getLineItems().get(2);
        assertThat(item3.getProductName()).isEqualTo("RAU CAI XANH DALAT 300G");
        assertThat(item3.getQuantity()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(item3.getUnitPrice()).isEqualByComparingTo(new BigDecimal("63000"));
        assertThat(item3.getLineAmount()).isEqualByComparingTo(new BigDecimal("126000"));

        // Verify total amount matches exactly 350.000
        assertThat(parsed.getDetectedTotalAmount()).isEqualByComparingTo(new BigDecimal("350000"));
        assertThat(parsed.getCalculatedSumAmount()).isEqualByComparingTo(new BigDecimal("350000"));

        // Verify that TIEN KHACH DUA (500.000) and TIEN TRA LAI (150.000) were NOT added as line items
        boolean containsPaymentLines = parsed.getLineItems().stream()
                .anyMatch(i -> i.getProductName().contains("KHÁCH ĐƯA") || i.getProductName().contains("TRẢ LẠI"));
        assertThat(containsPaymentLines).isFalse();
    }

    @Test
    @DisplayName("Test 3 - End-to-End OCR to Double-Entry Ledger: Balanced journal entry with algebraic SUM = 0.0000")
    void testEndToEndOcrToDoubleEntryLedger() {
        // Train N-gram Categorizer with food and grocery accounts
        localCategorizer.train("thit ba chi heo", foodExpenseAccountId);
        localCategorizer.train("ca hoi na uy", foodExpenseAccountId);
        localCategorizer.train("rau cai xanh", foodExpenseAccountId);

        when(bankAccountMappingRepository.findByBankAccountNumber("1903666888999"))
                .thenReturn(Optional.of(mapping));

        UUID generatedJournalId = UUID.randomUUID();
        when(ledgerService.recordTransaction(any(RecordTransactionRequest.class)))
                .thenAnswer(invocation -> {
                    RecordTransactionRequest req = invocation.getArgument(0);
                    // Verify double-entry balancing invariant strictly: SUM(legs) must equal 0.0000
                    BigDecimal sum = req.getPostingLegs().stream()
                            .map(PostingLegRequest::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(4, RoundingMode.HALF_EVEN);

                    assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO.setScale(4));

                    return JournalEntryResponse.builder()
                            .id(generatedJournalId)
                            .transactionDate(req.getTransactionDate())
                            .description(req.getDescription())
                            .referenceId(req.getReferenceId())
                            .build();
                });

        // 1. Raw OCR input boxes from receipt
        List<OcrTextBox> boxes = List.of(
                new OcrTextBox("WINMART+ LE DUAN", 40, 20, 250, 40, 0.98),
                new OcrTextBox("THIT BA CHI HEO KHAY 500G", 40, 120, 310, 140, 0.99),
                new OcrTextBox("1 x 120.000 120.000", 50, 150, 320, 170, 0.96),
                new OcrTextBox("CA HOI NA UY FILLET", 40, 190, 260, 210, 0.97),
                new OcrTextBox("0.52 kg x 200.000 104.000", 50, 220, 330, 240, 0.98),
                new OcrTextBox("RAU CAI XANH DALAT 300G", 40, 260, 290, 280, 0.98),
                new OcrTextBox("2 x 63.000 126.000", 50, 290, 310, 310, 0.97),
                new OcrTextBox("TỔNG CỘNG: 350.000", 40, 340, 320, 360, 0.99)
        );

        // 2. Spatial Layout Parsing
        List<ReconstructedReceiptRow> rows = spatialParser.parse(boxes);

        // 3. Receipt Line Item Extraction
        ParsedReceiptResult parsed = lineItemExtractor.extract(rows);
        assertThat(parsed.getLineItems()).hasSize(3);

        // 4. Ingest into Double-Entry Ledger
        ReceiptIngestionResponse response = splitIngestionService.ingestParsedReceipt(
                "1903666888999",
                parsed,
                new BigDecimal("350000")
        );

        // 5. Verification
        assertThat(response).isNotNull();
        assertThat(response.getJournalEntryId()).isEqualTo(generatedJournalId);
        assertThat(response.getStatus()).isEqualTo("PROCESSED");
        assertThat(response.getTotalPaidAmount()).isEqualByComparingTo(new BigDecimal("350000"));

        // Capture the transaction recorded into LedgerService
        ArgumentCaptor<RecordTransactionRequest> captor = ArgumentCaptor.forClass(RecordTransactionRequest.class);
        verify(ledgerService, times(1)).recordTransaction(captor.capture());

        RecordTransactionRequest capturedReq = captor.getValue();
        List<PostingLegRequest> legs = capturedReq.getPostingLegs();

        // 3 expense legs + 1 bank credit leg = 4 posting legs
        assertThat(legs).hasSize(4);

        // Sum of all legs must equal 0.0000
        BigDecimal algebraicSum = legs.stream()
                .map(PostingLegRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_EVEN);
        assertThat(algebraicSum).isEqualByComparingTo(BigDecimal.ZERO.setScale(4));

        // Check Bank credit leg: must equal -350000.0000
        PostingLegRequest bankLeg = legs.stream()
                .filter(l -> l.getAccountId().equals(bankLedgerAccountId))
                .findFirst()
                .orElseThrow();
        assertThat(bankLeg.getAmount()).isEqualByComparingTo(new BigDecimal("-350000.0000"));

        // Check Expense debit legs: sum of debits must equal +350000.0000
        BigDecimal expenseDebits = legs.stream()
                .filter(l -> !l.getAccountId().equals(bankLedgerAccountId))
                .map(PostingLegRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_EVEN);
        assertThat(expenseDebits).isEqualByComparingTo(new BigDecimal("350000.0000"));
    }
}
