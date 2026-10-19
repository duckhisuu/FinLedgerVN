package vn.finledger.ingestion.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import vn.finledger.ingestion.dto.ReceiptIngestionResponse;
import vn.finledger.ingestion.ocr.OcrTextBox;
import vn.finledger.ingestion.ocr.OcrVisionClient;
import vn.finledger.ingestion.ocr.ParsedReceiptResult;
import vn.finledger.ingestion.ocr.ReceiptLineItemDto;
import vn.finledger.ingestion.ocr.ReconstructedReceiptRow;
import vn.finledger.ingestion.ocr.SpatialReceiptLayoutParser;
import vn.finledger.ingestion.ocr.VietnameseReceiptLineItemExtractor;
import vn.finledger.ingestion.service.ReceiptSplitIngestionService;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReceiptOcrControllerTest {

    @Mock
    private OcrVisionClient ocrVisionClient;

    @Mock
    private SpatialReceiptLayoutParser spatialReceiptLayoutParser;

    @Mock
    private VietnameseReceiptLineItemExtractor receiptLineItemExtractor;

    @Mock
    private ReceiptSplitIngestionService receiptSplitIngestionService;

    @InjectMocks
    private ReceiptOcrController controller;

    private byte[] dummyImageBytes;

    @BeforeEach
    void setUp() {
        dummyImageBytes = new byte[]{1, 2, 3, 4};
    }

    @Test
    @DisplayName("Should successfully upload receipt and return 200 PROCESSED")
    void testUploadReceiptSuccess() {
        MockMultipartFile image = new MockMultipartFile(
                "image", "receipt.jpg", "image/jpeg", dummyImageBytes);

        List<OcrTextBox> boxes = List.of(
                new OcrTextBox("WINMART", 10, 10, 100, 30, 0.99),
                new OcrTextBox("SUA TUOI 64.000", 10, 40, 200, 60, 0.98)
        );

        List<ReconstructedReceiptRow> rows = List.of(
                new ReconstructedReceiptRow(boxes)
        );

        ParsedReceiptResult parsed = ParsedReceiptResult.builder()
                .lineItems(List.of(
                        ReceiptLineItemDto.builder()
                                .productName("SUA TUOI")
                                .quantity(BigDecimal.ONE)
                                .lineAmount(new BigDecimal("64000"))
                                .build()
                ))
                .detectedTotalAmount(new BigDecimal("64000"))
                .merchantName("WINMART")
                .build();

        ReceiptIngestionResponse expectedResponse = ReceiptIngestionResponse.builder()
                .journalEntryId(UUID.randomUUID())
                .status("PROCESSED")
                .merchantName("WINMART")
                .totalPaidAmount(new BigDecimal("64000"))
                .legCount(2)
                .build();

        when(ocrVisionClient.extractTextBoxes(any(), any())).thenReturn(boxes);
        when(spatialReceiptLayoutParser.parse(boxes)).thenReturn(rows);
        when(receiptLineItemExtractor.extract(rows)).thenReturn(parsed);
        when(receiptSplitIngestionService.ingestParsedReceipt(eq("1903666888999"), eq(parsed), any()))
                .thenReturn(expectedResponse);

        ResponseEntity<?> response = controller.uploadReceiptImage(
                image, "1903666888999", new BigDecimal("64000"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedResponse);
    }

    @Test
    @DisplayName("Should reject file upload exceeding 5MB with 400 Bad Request")
    void testUploadReceiptFileTooLarge() {
        // Create 6MB payload
        byte[] largeBytes = new byte[6 * 1024 * 1024];
        MockMultipartFile largeImage = new MockMultipartFile(
                "image", "large.png", "image/png", largeBytes);

        ResponseEntity<?> response = controller.uploadReceiptImage(
                largeImage, "1903666888999", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("error").toString()).contains("exceeds maximum allowed limit of 5MB");
    }

    @Test
    @DisplayName("Should reject unsupported MIME type with 400 Bad Request")
    void testUploadReceiptUnsupportedMimeType() {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "image", "document.pdf", "application/pdf", dummyImageBytes);

        ResponseEntity<?> response = controller.uploadReceiptImage(
                pdfFile, "1903666888999", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("error").toString()).contains("Unsupported file type");
    }

    @Test
    @DisplayName("Should reject empty image file with 400 Bad Request")
    void testUploadReceiptEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "image", "empty.jpg", "image/jpeg", new byte[0]);

        ResponseEntity<?> response = controller.uploadReceiptImage(
                emptyFile, "1903666888999", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("error").toString()).contains("Image file is required");
    }

    @Test
    @DisplayName("Should reject when no OCR boxes are detected with 400 Bad Request")
    void testUploadReceiptNoOcrDetected() {
        MockMultipartFile image = new MockMultipartFile(
                "image", "receipt.webp", "image/webp", dummyImageBytes);

        when(ocrVisionClient.extractTextBoxes(any(), any())).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = controller.uploadReceiptImage(
                image, "1903666888999", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("error").toString()).contains("No text detected");
    }
}
