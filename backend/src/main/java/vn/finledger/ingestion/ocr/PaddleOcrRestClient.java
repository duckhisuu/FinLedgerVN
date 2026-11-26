package vn.finledger.ingestion.ocr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * REST client connecting to the internal Python PaddleOCR micro-worker.
 */
@Slf4j
@Component
public class PaddleOcrRestClient implements OcrVisionClient {

    private final RestClient restClient;
    private final String ocrWorkerUrl;

    public PaddleOcrRestClient(
            @Value("${ocr.worker.url:http://localhost:8081}") String ocrWorkerUrl) {
        String normalizedUrl = ocrWorkerUrl != null ? ocrWorkerUrl.trim().replaceAll("/+$", "") : "http://localhost:8081";
        this.ocrWorkerUrl = normalizedUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(120));

        this.restClient = RestClient.builder()
                .baseUrl(normalizedUrl)
                .requestFactory(requestFactory)
                .build();
    }


    @Override
    public List<OcrTextBox> extractTextBoxes(byte[] imageBytes, String filename) {
        if (imageBytes == null || imageBytes.length == 0) {
            return Collections.emptyList();
        }

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return filename != null ? filename : "receipt.jpg";
                }
            };
            body.add("file", resource);

            List<OcrTextBox> response = restClient.post()
                    .uri("/predict")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<OcrTextBox>>() {});

            return response != null ? response : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Failed to call OCR worker at {}: {}.", ocrWorkerUrl, e.getMessage());
            throw new RuntimeException("Dịch vụ quét hóa đơn (OCR) hiện tạm thời không phản hồi. Vui lòng thử lại sau vài giây hoặc nhập giao dịch thủ công.", e);
        }
    }
}
