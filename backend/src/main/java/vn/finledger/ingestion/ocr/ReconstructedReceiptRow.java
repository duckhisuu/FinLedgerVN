package vn.finledger.ingestion.ocr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a single reconstructed horizontal row on a receipt,
 * aggregated from spatially clustered OCR bounding boxes.
 */
public class ReconstructedReceiptRow {

    private final List<OcrTextBox> boxes = new ArrayList<>();

    public ReconstructedReceiptRow() {
    }

    public ReconstructedReceiptRow(List<OcrTextBox> initialBoxes) {
        if (initialBoxes != null) {
            this.boxes.addAll(initialBoxes);
        }
    }

    public void addBox(OcrTextBox box) {
        if (box != null) {
            this.boxes.add(box);
        }
    }

    public List<OcrTextBox> getBoxes() {
        return Collections.unmodifiableList(boxes);
    }

    public double getAvgY() {
        if (boxes.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (OcrTextBox box : boxes) {
            sum += box.yCenter();
        }
        return sum / boxes.size();
    }

    public void sortLeftToRight() {
        boxes.sort(Comparator.comparingDouble(OcrTextBox::xMin));
    }

    public String getJoinedText() {
        return boxes.stream()
                .map(OcrTextBox::text)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining(" "))
                .trim();
    }

    public OcrTextBox getFirstBox() {
        return boxes.isEmpty() ? null : boxes.get(0);
    }

    public OcrTextBox getLastBox() {
        return boxes.isEmpty() ? null : boxes.get(boxes.size() - 1);
    }

    public int size() {
        return boxes.size();
    }

    public boolean isEmpty() {
        return boxes.isEmpty();
    }

    @Override
    public String toString() {
        return "ReceiptRow[avgY=" + String.format("%.2f", getAvgY()) + ", text='" + getJoinedText() + "']";
    }
}
