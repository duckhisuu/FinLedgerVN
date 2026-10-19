package vn.finledger.ingestion.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable spatial bounding box representing a recognized text token from OCR.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OcrTextBox(
        @JsonProperty("text") String text,
        @JsonProperty("xMin") double xMin,
        @JsonProperty("yMin") double yMin,
        @JsonProperty("xMax") double xMax,
        @JsonProperty("yMax") double yMax,
        @JsonProperty("confidence") double confidence
) {
    public double yCenter() {
        return (yMin + yMax) / 2.0;
    }

    public double height() {
        return yMax - yMin;
    }

    public double xCenter() {
        return (xMin + xMax) / 2.0;
    }

    public double width() {
        return xMax - xMin;
    }
}
