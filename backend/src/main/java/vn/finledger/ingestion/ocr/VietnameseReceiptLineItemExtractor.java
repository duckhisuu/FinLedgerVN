package vn.finledger.ingestion.ocr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * State Machine parser for Vietnamese supermarket and convenience store receipts.
 * Handles both Single-Line items and Two-Line wrapped items (WinMart, Bach Hoa Xanh, Circle K, CoopMart),
 * robust VND currency normalization (thermal printer OCR artifacts 'O'/'o' -> '0'),
 * and automatic Header/Footer boundary zone filtering.
 */
@Slf4j
@Component
public class VietnameseReceiptLineItemExtractor {

    // Regex for Two-line formula row:
    // e.g., "1   x   120.000      120.000" or "0.52 kg x 200.000   104.000" or "1 x 120.00O 120.00O"
    private static final Pattern FORMULA_QTY_PRICE_AMOUNT = Pattern.compile(
            "^\\s*([0-9]+(?:[.,][0-9]+)?)\\s*(?:kg|g|lon|chai|hop|goi|cai|khay|bao|tui|ve)?\\s*[*xX]\\s*([0-9oO.,]+)\\s+([0-9oO.,]+(?:[đĐ]|VND|vnd)?)\\s*$"
    );

    // Alternative: "1   120.000   120.000"
    private static final Pattern FORMULA_SPACE_SEPARATED = Pattern.compile(
            "^\\s*([0-9]+(?:[.,][0-9]+)?)\\s+([0-9oO.,]{4,})\\s+([0-9oO.,]{4,}(?:[đĐ]|VND|vnd)?)\\s*$"
    );

    // Alternative: "2   x   64.000" (qty x total amount or qty x unit price)
    private static final Pattern FORMULA_QTY_X_AMOUNT = Pattern.compile(
            "^\\s*([0-9]+(?:[.,][0-9]+)?)\\s*(?:kg|g|lon|chai|hop|goi|cai|khay|bao|tui|ve)?\\s*[*xX]\\s*([0-9oO.,]{4,}(?:[đĐ]|VND|vnd)?)\\s*$"
    );

    // Pure quantity token: e.g. "1", "2", "10"
    private static final Pattern PURE_QUANTITY_PATTERN = Pattern.compile(
            "^[0-9]+(?:[.,][0-9]+)?$"
    );

    private static final Set<String> FOOTER_KEYWORDS = Set.of(
            "TONG CONG", "TONG TIEN", "THANH TOAN", "PHAI THU", "TONG PHAI TRA",
            "TIEN MAT", "CHUYEN KHOAN", "TOTAL", "GRAND TOTAL", "AMOUNT DUE"
    );

    private static final Set<String> HEADER_KEYWORDS = Set.of(
            "CONG TY", "WINCOMMERCE", "WINMART", "BACH HOA XANH", "CIRCLE K", "COOPMART",
            "DIA CHI", "ADDRESS", "TEL", "SDT", "SO DT", "DT:", "MST", "MA SO THUE",
            "HOA DON", "PHIEU THANH TOAN", "THU NGAN", "CASHIER", "BAN IN", "SO PHIEU",
            "NGAY", "DATE", "GIO", "TIME", "STT", "TEN HANG", "SO LUONG", "DON GIA",
            "THANH TIEN", "TEN SAN PHAM", "D.GIA", "T.TIEN", "SL"
    );

    /**
     * Extracts structured line items and totals from reconstructed receipt rows.
     */
    public ParsedReceiptResult extract(List<ReconstructedReceiptRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return ParsedReceiptResult.builder().build();
        }

        List<ReceiptLineItemDto> lineItems = new ArrayList<>();
        BigDecimal detectedTotalAmount = null;
        String merchantName = null;
        String pendingItemName = null;

        for (int i = 0; i < rows.size(); i++) {
            ReconstructedReceiptRow row = rows.get(i);
            String rowText = row.getJoinedText().trim();
            if (rowText.isBlank()) {
                continue;
            }

            String normalizedText = removeDiacritics(rowText).toUpperCase();

            // Detect merchant name from early header rows if possible
            if (merchantName == null && i < 3) {
                if (normalizedText.contains("WINMART") || normalizedText.contains("BACH HOA XANH")
                        || normalizedText.contains("CIRCLE K") || normalizedText.contains("COOPMART")) {
                    merchantName = rowText;
                }
            }

            // 1. Zone Filtering: Check for Footer (Termination keywords)
            if (isFooterRow(normalizedText)) {
                log.debug("Encountered footer row: '{}'. Terminating item parsing.", rowText);
                detectedTotalAmount = extractAmountFromRow(row);
                // If not found directly, try parsing from the row text
                if (detectedTotalAmount == null) {
                    detectedTotalAmount = extractTrailingAmount(rowText);
                }
                // STOP extracting items immediately to avoid picking up Paid / Change / VAT lines
                break;
            }

            // 2. Zone Filtering: Check for Header keywords
            if (isHeaderRow(normalizedText)) {
                log.debug("Skipping header row: '{}'", rowText);
                pendingItemName = null;
                continue;
            }

            // 3. Check if current row is a Formula Row (Quantity x Price = Total)
            if (isFormulaRow(rowText)) {
                if (pendingItemName != null) {
                    ReceiptLineItemDto twoLineItem = tryParseFormulaRow(pendingItemName, rowText);
                    if (twoLineItem != null) {
                        lineItems.add(twoLineItem);
                        log.debug("Extracted two-line item: {}", twoLineItem);
                        pendingItemName = null;
                        continue;
                    }
                }
                // Formula row without pending item name cannot be a single-line item
                pendingItemName = null;
                continue;
            }

            // 4. Try matching Single-Line Item (Product name on left, qty & amount on right)
            ReceiptLineItemDto singleLineItem = tryParseSingleLineItem(row);
            if (singleLineItem != null) {
                lineItems.add(singleLineItem);
                log.debug("Extracted single-line item: {}", singleLineItem);
                pendingItemName = null;
                continue;
            }

            // 5. If row does not end with price, and contains alphabetic text, it could be
            // the product name for a two-line item (e.g. "THIT BA CHI HEO KHAY 500G")
            if (isCandidateProductName(rowText, normalizedText)) {
                if (pendingItemName != null) {
                    // Previous pending name was followed by another name line -> concatenate wrapped name
                    pendingItemName = pendingItemName + " " + rowText;
                } else {
                    pendingItemName = rowText;
                }
                log.debug("Set pending item name: '{}'", pendingItemName);
            } else {
                // Not a product candidate and not an item
                pendingItemName = null;
            }
        }

        // Calculate sum of extracted items
        BigDecimal calculatedSum = lineItems.stream()
                .map(ReceiptLineItemDto::getLineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_EVEN);

        if (detectedTotalAmount == null) {
            detectedTotalAmount = calculatedSum;
        }

        return ParsedReceiptResult.builder()
                .lineItems(lineItems)
                .detectedTotalAmount(detectedTotalAmount)
                .calculatedSumAmount(calculatedSum)
                .merchantName(merchantName)
                .build();
    }

    /**
     * Checks if a row matches a two-line quantity/price formula.
     */
    public boolean isFormulaRow(String rowText) {
        if (rowText == null || rowText.isBlank()) {
            return false;
        }
        String trimmed = rowText.trim();
        return FORMULA_QTY_PRICE_AMOUNT.matcher(trimmed).matches()
                || FORMULA_SPACE_SEPARATED.matcher(trimmed).matches()
                || FORMULA_QTY_X_AMOUNT.matcher(trimmed).matches()
                || trimmed.matches("^\\s*[0-9]+(?:[.,][0-9]+)?\\s*(?:kg|g|lon|chai|hop|goi|cai|khay|bao|tui|ve)?\\s*[*xX].*");
    }

    /**
     * Checks if row is a footer row marking the end of the item list.
     */
    private boolean isFooterRow(String normalizedText) {
        for (String keyword : FOOTER_KEYWORDS) {
            if (normalizedText.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if row is a header row (store info, address, column titles).
     */
    private boolean isHeaderRow(String normalizedText) {
        for (String keyword : HEADER_KEYWORDS) {
            if (normalizedText.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if text qualifies as a product name candidate (has letters, length >= 3, not pure numbers).
     */
    private boolean isCandidateProductName(String rowText, String normalizedText) {
        if (rowText.length() < 3) {
            return false;
        }
        // Must contain at least some letters
        long letterCount = rowText.chars().filter(Character::isLetter).count();
        if (letterCount < 2) {
            return false;
        }
        // Should not end with a price
        BigDecimal trailingAmount = extractTrailingAmount(rowText);
        return trailingAmount == null;
    }

    /**
     * Tries to parse a two-line formula row following a pending product name.
     */
    private ReceiptLineItemDto tryParseFormulaRow(String productName, String rowText) {
        // Pattern 1: Qty x UnitPrice TotalAmount (e.g. "1   x   120.000      120.000" or "0.52 kg x 200.000   104.000")
        Matcher m1 = FORMULA_QTY_PRICE_AMOUNT.matcher(rowText);
        if (m1.matches()) {
            BigDecimal qty = parseQuantity(m1.group(1));
            BigDecimal unitPrice = parseVietnameseAmount(m1.group(2));
            BigDecimal amount = parseVietnameseAmount(m1.group(3));
            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                return ReceiptLineItemDto.builder()
                        .productName(cleanProductName(productName))
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .lineAmount(amount)
                        .build();
            }
        }

        // Pattern 2: Space-separated "1 120.000 120.000"
        Matcher m2 = FORMULA_SPACE_SEPARATED.matcher(rowText);
        if (m2.matches()) {
            BigDecimal qty = parseQuantity(m2.group(1));
            BigDecimal unitPrice = parseVietnameseAmount(m2.group(2));
            BigDecimal amount = parseVietnameseAmount(m2.group(3));
            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                return ReceiptLineItemDto.builder()
                        .productName(cleanProductName(productName))
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .lineAmount(amount)
                        .build();
            }
        }

        // Pattern 3: "2 x 64.000"
        Matcher m3 = FORMULA_QTY_X_AMOUNT.matcher(rowText);
        if (m3.matches()) {
            BigDecimal qty = parseQuantity(m3.group(1));
            BigDecimal parsedAmt = parseVietnameseAmount(m3.group(2));
            if (parsedAmt != null && parsedAmt.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal unitPrice = parsedAmt;
                BigDecimal lineAmount = parsedAmt;
                if (qty.compareTo(BigDecimal.ONE) > 0) {
                    lineAmount = parsedAmt.multiply(qty).setScale(4, RoundingMode.HALF_EVEN);
                }
                return ReceiptLineItemDto.builder()
                        .productName(cleanProductName(productName))
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .lineAmount(lineAmount)
                        .build();
            }
        }

        return null;
    }

    /**
     * Tries to parse a single-line item: Product name on left, qty and amount on right.
     */
    private ReceiptLineItemDto tryParseSingleLineItem(ReconstructedReceiptRow row) {
        String fullText = row.getJoinedText().trim();
        List<OcrTextBox> boxes = row.getBoxes();
        if (boxes.isEmpty()) {
            return null;
        }

        // If the row looks like a formula row, it's not a single-line item
        if (isFormulaRow(fullText)) {
            return null;
        }

        // Check if the last box (or rightmost token) represents a price amount
        OcrTextBox lastBox = boxes.get(boxes.size() - 1);
        BigDecimal lineAmount = parseVietnameseAmount(lastBox.text());
        int amountBoxIndex = boxes.size() - 1;

        if (lineAmount == null) {
            // Try extracting trailing amount from the full string
            lineAmount = extractTrailingAmount(fullText);
            if (lineAmount == null) {
                return null;
            }
        }

        // Amount must be reasonable for an item (at least 1,000 VND)
        if (lineAmount.compareTo(new BigDecimal("1000")) < 0) {
            return null;
        }

        // Now determine quantity and product name from the remaining preceding boxes/text
        BigDecimal quantity = BigDecimal.ONE.setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal unitPrice = lineAmount;
        StringBuilder nameBuilder = new StringBuilder();

        if (boxes.size() >= 3) {
            // Check if second-to-last box is quantity
            OcrTextBox secondLast = boxes.get(amountBoxIndex - 1);
            if (isQuantityToken(secondLast.text())) {
                quantity = parseQuantity(secondLast.text());
                for (int i = 0; i < amountBoxIndex - 1; i++) {
                    nameBuilder.append(boxes.get(i).text()).append(" ");
                }
            } else {
                for (int i = 0; i < amountBoxIndex; i++) {
                    nameBuilder.append(boxes.get(i).text()).append(" ");
                }
            }
        } else if (boxes.size() == 2) {
            nameBuilder.append(boxes.get(0).text());
        } else {
            // Single box containing everything, split by space tokens
            String[] tokens = fullText.split("\\s+");
            if (tokens.length >= 3 && isQuantityToken(tokens[tokens.length - 2])) {
                quantity = parseQuantity(tokens[tokens.length - 2]);
                for (int k = 0; k < tokens.length - 2; k++) {
                    nameBuilder.append(tokens[k]).append(" ");
                }
            } else if (tokens.length >= 2) {
                for (int k = 0; k < tokens.length - 1; k++) {
                    nameBuilder.append(tokens[k]).append(" ");
                }
            } else {
                return null;
            }
        }

        String rawName = nameBuilder.toString().trim();
        if (rawName.length() < 2 || !containsLetters(rawName)) {
            return null;
        }

        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
            unitPrice = lineAmount.divide(quantity, 4, RoundingMode.HALF_EVEN);
        }

        return ReceiptLineItemDto.builder()
                .productName(cleanProductName(rawName))
                .quantity(quantity)
                .unitPrice(unitPrice)
                .lineAmount(lineAmount)
                .build();
    }

    /**
     * Extracts an amount from a reconstructed row (scans boxes right-to-left).
     */
    public BigDecimal extractAmountFromRow(ReconstructedReceiptRow row) {
        List<OcrTextBox> boxes = row.getBoxes();
        for (int i = boxes.size() - 1; i >= 0; i--) {
            BigDecimal amt = parseVietnameseAmount(boxes.get(i).text());
            if (amt != null && amt.compareTo(new BigDecimal("1000")) >= 0) {
                return amt;
            }
        }
        return extractTrailingAmount(row.getJoinedText());
    }

    /**
     * Extracts the trailing amount from a line of text.
     */
    public BigDecimal extractTrailingAmount(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] tokens = text.trim().split("\\s+");
        for (int i = tokens.length - 1; i >= 0; i--) {
            BigDecimal amt = parseVietnameseAmount(tokens[i]);
            if (amt != null && amt.compareTo(new BigDecimal("1000")) >= 0) {
                return amt;
            }
        }
        return null;
    }

    /**
     * Parses and normalizes Vietnamese money strings:
     * Handles "120.000", "120,000", "120.000đ", "120.000 VND",
     * and thermal OCR errors confusing 'O'/'o' with '0' (e.g. "120.00O" -> 120000).
     */
    public BigDecimal parseVietnameseAmount(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }

        String text = rawText.trim();

        // Reject if token is a package size/specification (e.g. 500G, 300g, 180ML, 1L, 2KG, 330ml)
        if (text.matches("(?i)^[0-9]+(?:[.,][0-9]+)?\\s*(?:g|gr|kg|ml|l|cm|mm|m|pcs|hop|chai|lon)$")) {
            return null;
        }

        // If string contains multiple space-separated words, do not parse as a single amount
        // unless it's like "120.000 VND" or "120.000 đ"
        if (text.contains(" ") && !text.matches("(?i)^[0-9oO.,]+\\s*(?:[đĐ]|vnd)$")) {
            return null;
        }

        // Remove currency symbols & suffixes
        text = text.replaceAll("(?i)[đĐ]|VND|vnd", "").trim();

        // Check if string contains at least some digits or 'o'/'O'
        if (!text.matches(".*\\d.*") && !text.matches(".*[oO].*")) {
            return null;
        }

        // Classic thermal printer OCR correction: replace 'O' or 'o' with '0' in price patterns
        // E.g. "120.00O" -> "120.000", "12O.OOO" -> "120.000"
        String corrected = text.replaceAll("(?i)o", "0");

        // Remove thousand separators (dots or commas) for VND integer currency
        String digitsOnly = corrected.replaceAll("[^0-9]", "");
        if (digitsOnly.isBlank()) {
            return null;
        }

        try {
            BigDecimal amount = new BigDecimal(digitsOnly).setScale(4, RoundingMode.HALF_EVEN);
            // Minimum retail receipt amount in Vietnam is at least 1,000 VND
            if (amount.compareTo(new BigDecimal("1000")) < 0) {
                return null;
            }
            return amount;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses quantity string: e.g. "1", "2", "0.52 kg", "1.5".
     */
    public BigDecimal parseQuantity(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return BigDecimal.ONE.setScale(4, RoundingMode.HALF_EVEN);
        }

        String cleaned = rawText.trim()
                .replaceAll("(?i)kg|g|lon|chai|hop|goi|cai|khay|bao|tui|ve", "")
                .trim();

        // If decimal using comma (e.g. "0,52" -> "0.52")
        cleaned = cleaned.replace(',', '.');

        try {
            return new BigDecimal(cleaned).setScale(4, RoundingMode.HALF_EVEN);
        } catch (Exception e) {
            return BigDecimal.ONE.setScale(4, RoundingMode.HALF_EVEN);
        }
    }

    private boolean isQuantityToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return PURE_QUANTITY_PATTERN.matcher(token.trim()).matches();
    }

    private boolean containsLetters(String text) {
        return text.chars().anyMatch(Character::isLetter);
    }

    private String cleanProductName(String name) {
        if (name == null) {
            return "";
        }
        // Remove leading sequence numbers like "1. ", "01. "
        return name.replaceAll("^\\d+[.)-]\\s*", "").trim();
    }

    /**
     * Strips Vietnamese accents/diacritics for uniform keyword comparison.
     */
    public static String removeDiacritics(String str) {
        if (str == null) {
            return "";
        }
        String nfd = Normalizer.normalize(str, Normalizer.Form.NFD);
        String withoutAccents = nfd.replaceAll("\\p{M}", "");
        // Handle Vietnamese Đ/đ
        return withoutAccents.replace('Đ', 'D').replace('đ', 'd');
    }
}
