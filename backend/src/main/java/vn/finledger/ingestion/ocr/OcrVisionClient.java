package vn.finledger.ingestion.ocr;

import java.util.List;

/**
 * Interface for OCR vision client that processes image bytes into spatial text boxes.
 */
public interface OcrVisionClient {

    /**
     * Extracts text boxes with spatial bounding coordinates and confidence scores from an image.
     *
     * @param imageBytes raw image bytes
     * @param filename   original file name
     * @return list of OCR text boxes
     */
    List<OcrTextBox> extractTextBoxes(byte[] imageBytes, String filename);
}
