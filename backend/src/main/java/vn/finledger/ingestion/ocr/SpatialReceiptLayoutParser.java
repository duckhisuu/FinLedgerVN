package vn.finledger.ingestion.ocr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 2D Spatial Layout Parser for OCR Text Boxes.
 * Clusters scattered bounding boxes into coherent horizontal lines using
 * Y-axis tolerance clustering, and restores natural reading order via X-axis sorting.
 */
@Slf4j
@Component
public class SpatialReceiptLayoutParser {

    private static final double DEFAULT_MEDIAN_HEIGHT = 15.0;
    private static final double Y_TOLERANCE_RATIO = 0.6;

    /**
     * Reconstructs receipt rows from raw OCR bounding boxes.
     *
     * @param rawBoxes the unstructured OCR bounding boxes
     * @return ordered list of reconstructed receipt rows
     */
    public List<ReconstructedReceiptRow> parse(List<OcrTextBox> rawBoxes) {
        if (rawBoxes == null || rawBoxes.isEmpty()) {
            return Collections.emptyList();
        }

        // Filter out invalid/empty boxes
        List<OcrTextBox> validBoxes = rawBoxes.stream()
                .filter(b -> b != null && b.text() != null && !b.text().isBlank())
                .toList();

        if (validBoxes.isEmpty()) {
            return Collections.emptyList();
        }

        // Calculate median box height across all text boxes
        double medianHeight = calculateMedianHeight(validBoxes);
        double yTolerance = Y_TOLERANCE_RATIO * medianHeight;
        log.debug("Computed medianHeight={}, yTolerance={}", medianHeight, yTolerance);

        // Step 1: Sort by yCenter ascending for vertical clustering
        List<OcrTextBox> sortedByY = new ArrayList<>(validBoxes);
        sortedByY.sort(Comparator.comparingDouble(OcrTextBox::yCenter));

        List<ReconstructedReceiptRow> rows = new ArrayList<>();
        ReconstructedReceiptRow currentRow = null;

        for (OcrTextBox box : sortedByY) {
            if (currentRow == null || Math.abs(box.yCenter() - currentRow.getAvgY()) > yTolerance) {
                // Start a new line
                currentRow = new ReconstructedReceiptRow();
                currentRow.addBox(box);
                rows.add(currentRow);
            } else {
                // Box falls within vertical tolerance of the current line
                currentRow.addBox(box);
            }
        }

        // Step 2: X-Axis Left-to-Right Sorting within each line
        for (ReconstructedReceiptRow row : rows) {
            row.sortLeftToRight();
        }

        log.debug("Reconstructed {} rows from {} OCR boxes", rows.size(), validBoxes.size());
        return rows;
    }

    /**
     * Calculates the median text box height.
     */
    public double calculateMedianHeight(List<OcrTextBox> boxes) {
        List<Double> heights = boxes.stream()
                .map(OcrTextBox::height)
                .filter(h -> h > 0)
                .sorted()
                .toList();

        if (heights.isEmpty()) {
            return DEFAULT_MEDIAN_HEIGHT;
        }

        int size = heights.size();
        if (size % 2 == 1) {
            return heights.get(size / 2);
        } else {
            return (heights.get(size / 2 - 1) + heights.get(size / 2)) / 2.0;
        }
    }
}
