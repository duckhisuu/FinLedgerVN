package vn.finledger.ingestion.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.finledger.ingestion.dto.ReceiptIngestionResponse;
import vn.finledger.ingestion.ocr.OcrTextBox;
import vn.finledger.ingestion.ocr.OcrVisionClient;
import vn.finledger.ingestion.ocr.ParsedReceiptResult;
import vn.finledger.ingestion.ocr.ReconstructedReceiptRow;
import vn.finledger.ingestion.ocr.SpatialReceiptLayoutParser;
import vn.finledger.ingestion.ocr.VietnameseReceiptLineItemExtractor;
import vn.finledger.ingestion.service.ReceiptSplitIngestionService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Controller for receipt image upload, OCR processing, and automated ledger ingestion.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ingestion/receipts")
@RequiredArgsConstructor
public class ReceiptOcrController {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );

    private final OcrVisionClient ocrVisionClient;
    private final SpatialReceiptLayoutParser spatialReceiptLayoutParser;
    private final VietnameseReceiptLineItemExtractor receiptLineItemExtractor;
    private final ReceiptSplitIngestionService receiptSplitIngestionService;

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadReceiptImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam("bankAccountNumber") String bankAccountNumber,
            @RequestParam(value = "actualPaidAmount", required = false) BigDecimal actualPaidAmount) {

        // 1. File Security Validations
        if (image == null || image.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Image file is required and cannot be empty"));
        }

        if (image.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "File size exceeds maximum allowed limit of 5MB (size: " + image.getSize() + " bytes)"));
        }

        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Unsupported file type: " + contentType + ". Allowed types: image/jpeg, image/png, image/webp"));
        }

        if (bankAccountNumber == null || bankAccountNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "bankAccountNumber is required"));
        }

        try {
            // 2. OCR Vision extraction
            List<OcrTextBox> boxes = ocrVisionClient.extractTextBoxes(
                    image.getBytes(), image.getOriginalFilename());

            if (boxes == null || boxes.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No text detected on the uploaded receipt image"));
            }

            // 3. 2D Spatial Layout Parsing
            List<ReconstructedReceiptRow> rows = spatialReceiptLayoutParser.parse(boxes);

            // 4. Vietnamese Receipt Line Item Extraction
            ParsedReceiptResult parsedReceipt = receiptLineItemExtractor.extract(rows);
            if (parsedReceipt.getLineItems().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No line items could be parsed from the receipt image"));
            }

            // 5. Categorization and Double-Entry Ledger Recording
            ReceiptIngestionResponse response = receiptSplitIngestionService.ingestParsedReceipt(
                    bankAccountNumber, parsedReceipt, actualPaidAmount);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Validation error processing receipt: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Internal error processing receipt OCR: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process receipt image: " + e.getMessage()));
        }
    }
}
