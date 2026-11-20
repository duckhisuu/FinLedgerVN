package vn.finledger.ingestion.parsers;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser plugin for MoMo E-Wallet Push Notifications and Email Receipts.
 */
@Component
@Order(20)
public class MoMoWalletParserPlugin implements FinancialMessageParserPlugin {

    public static final String PROVIDER_MOMO = "MOMO";

    // Merchant payment: "Bạn vừa thanh toán 45.000đ cho CIRCLE K VIETNAM..."
    private static final Pattern PAYMENT_PATTERN = Pattern.compile(
            "(?:thanh\\s*toan|da\\s*tra|chi\\s*tieu)\\s+(?:thanh\\s*cong\\s+)?(\\d{1,3}(?:[.,]\\d{3})+|\\d+)\\s*(?:d|đ|vnd|vnđ)\\s+(?:cho|tai|den)\\s+([^.\\r\\n]+)",
            Pattern.CASE_INSENSITIVE
    );

    // Bank top-up / withdrawal: "Nạp tiền thành công 500.000đ vào Ví MoMo từ nguồn tiền Vietcombank..."
    private static final Pattern TOPUP_WITHDRAW_PATTERN = Pattern.compile(
            "(nap\\s*tien|rut\\s*tien)\\s+(?:thanh\\s*cong\\s+)?(\\d{1,3}(?:[.,]\\d{3})+|\\d+)\\s*(?:d|đ|vnd|vnđ)\\s+(?:vao|tu)\\s+vi\\s*momo\\s+(?:tu\\s+(?:nguon\\s+tien|ngan\\s+hang|tai\\s+khoan)|ve\\s+(?:ngan\\s+hang|tai\\s+khoan))\\s+([^.\\r\\n]+)",
            Pattern.CASE_INSENSITIVE
    );

    // P2P transfer / receive: "Bạn vừa nhận 150.000đ từ TRẦN VĂN B (0987***123)..." or "chuyển 100.000đ đến ..."
    private static final Pattern P2P_PATTERN = Pattern.compile(
            "(?:vua\\s+)?(nhan|duoc\\s+nhan|da\\s+nhan|chuyen|da\\s+chuyen|gui)\\s+(?:tien\\s+)?(\\d{1,3}(?:[.,]\\d{3})+|\\d+)\\s*(?:d|đ|vnd|vnđ)\\s+(?:tu|den|cho)\\s+([^.\\r\\n]+)",
            Pattern.CASE_INSENSITIVE
    );

    // Fallback generic MoMo amount
    private static final Pattern GENERIC_AMOUNT_PATTERN = Pattern.compile(
            "([+\\-])?\\s*(\\d{1,3}(?:[.,]\\d{3})+|\\d+)\\s*(?:d|đ|vnd|vnđ)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TRANS_ID_PATTERN = Pattern.compile(
            "(?:ma\\s*gd|ma\\s*giao\\s*dich|transid|transaction\\s*id)\\s*[:#]?\\s*([A-Za-z0-9]{6,20})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern BALANCE_PATTERN = Pattern.compile(
            "(?:so\\s*du(?:\\s*vi)?)\\s*[:.]?\\s*(\\d{1,3}(?:[.,]\\d{3})+|\\d+)\\s*(?:d|đ|vnd|vnđ)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern NOTE_PATTERN = Pattern.compile(
            "(?:noi\\s*dung|loi\\s*nhan|ghi\\s*chu)\\s*[:\\-]\\s*([^.\\r\\n]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MOMO_PHONE_PATTERN = Pattern.compile(
            "(?:vi\\s*momo|tk\\s*momo|sdt)\\s*[:.]?\\s*(0\\d{9})\\b",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public String providerCode() {
        return PROVIDER_MOMO;
    }

    @Override
    public boolean supports(String senderHint, String subject, String rawMessage, IngestionChannel channel) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return false;
        }
        String combinedUpper = VietnameseBankParsingSupport.normalizeUpper(
                (senderHint != null ? senderHint + " " : "") +
                (subject != null ? subject + " " : "") +
                rawMessage
        );

        // If message explicitly is a VCB balance SMS/push (e.g. SD TK ... MOMO), VCB plugin owns it
        if (combinedUpper.contains("SD TK ") || combinedUpper.contains("TK VCB") || combinedUpper.contains("MBVCB.")) {
            return false;
        }

        if (combinedUpper.contains("VI MOMO")
                || combinedUpper.contains("MA GD:")
                || combinedUpper.contains("MOMO")
                || combinedUpper.contains("LOI NHAN:")) {
            return true;
        }

        if (senderHint != null) {
            String s = senderHint.toUpperCase(Locale.ROOT);
            return s.contains("MOMO") || s.contains("MSERVICE");
        }
        return false;
    }

    @Override
    public Optional<ParsedBalanceEvent> parse(
            String senderHint,
            String subject,
            String rawMessage,
            IngestionChannel channel,
            Instant receivedAt
    ) {
        if (!supports(senderHint, subject, rawMessage, channel)) {
            return Optional.empty();
        }

        // We run regex matching against ASCII-normalized text so Vietnamese diacritics/casing always match reliably,
        // while still extracting original substring segments where helpful.
        String ascii = VietnameseBankParsingSupport.normalizeAscii(rawMessage);
        String upper = ascii.toUpperCase(Locale.ROOT);

        TransferDirection direction = null;
        BigDecimal amount = null;
        String counterparty = null;
        boolean linkedBankTransfer = false;
        String counterpartyProvider = null;
        String eventKindLabel = null;

        // 1. Check linked bank Top-up / Withdrawal first (Sample 3)
        Matcher topupMatcher = TOPUP_WITHDRAW_PATTERN.matcher(ascii);
        if (topupMatcher.find()) {
            String action = topupMatcher.group(1).toUpperCase(Locale.ROOT);
            direction = action.contains("NAP") ? TransferDirection.IN : TransferDirection.OUT;
            amount = VietnameseBankParsingSupport.parseVndAmount(topupMatcher.group(2)).orElse(null);
            String bankRaw = topupMatcher.group(3).trim();
            counterpartyProvider = VietnameseBankParsingSupport.detectLinkedBankProvider(bankRaw.toUpperCase(Locale.ROOT));
            counterparty = counterpartyProvider != null ? counterpartyProvider : bankRaw;
            linkedBankTransfer = true;
            eventKindLabel = direction == TransferDirection.IN
                    ? "Nap tien vao Vi MoMo tu " + bankRaw
                    : "Rut tien tu Vi MoMo ve " + bankRaw;
        }

        // 2. Check merchant payment (Sample 1)
        if (amount == null) {
            Matcher payMatcher = PAYMENT_PATTERN.matcher(ascii);
            if (payMatcher.find()) {
                direction = TransferDirection.OUT;
                amount = VietnameseBankParsingSupport.parseVndAmount(payMatcher.group(1)).orElse(null);
                counterparty = cleanCounterparty(payMatcher.group(2));
            }
        }

        // 3. Check P2P transfer/receive (Sample 2)
        if (amount == null) {
            Matcher p2pMatcher = P2P_PATTERN.matcher(ascii);
            if (p2pMatcher.find()) {
                String verb = p2pMatcher.group(1).toUpperCase(Locale.ROOT);
                direction = verb.contains("NHAN") ? TransferDirection.IN : TransferDirection.OUT;
                amount = VietnameseBankParsingSupport.parseVndAmount(p2pMatcher.group(2)).orElse(null);
                counterparty = cleanCounterparty(p2pMatcher.group(3));
            }
        }

        // 4. Generic fallback
        if (amount == null) {
            Matcher genMatcher = GENERIC_AMOUNT_PATTERN.matcher(ascii);
            if (!genMatcher.find()) {
                return Optional.empty();
            }
            String sign = genMatcher.group(1);
            if ("+".equals(sign) || upper.contains("NHAN ") || upper.contains("NAP TIEN") || upper.contains("HOAN TIEN")) {
                direction = TransferDirection.IN;
            } else {
                direction = TransferDirection.OUT;
            }
            amount = VietnameseBankParsingSupport.parseVndAmount(genMatcher.group(2)).orElse(null);
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || direction == null) {
            return Optional.empty();
        }

        // 5. Extract Transaction ID (11-digit MoMo Mã GD)
        String transId = null;
        Matcher idMatcher = TRANS_ID_PATTERN.matcher(ascii);
        if (idMatcher.find()) {
            transId = idMatcher.group(1).trim();
        }

        // 6. Extract running balance ("Số dư ví: 320.000đ" / "Số dư: 820.000đ")
        BigDecimal runningBalance = null;
        Matcher balMatcher = BALANCE_PATTERN.matcher(ascii);
        if (balMatcher.find()) {
            runningBalance = VietnameseBankParsingSupport.parseVndAmount(balMatcher.group(1)).orElse(null);
        }

        // 7. Extract optional user note / message ("Nội dung:" / "Lời nhắn:")
        String note = null;
        Matcher noteMatcher = NOTE_PATTERN.matcher(ascii);
        if (noteMatcher.find()) {
            note = noteMatcher.group(1).trim();
        }

        // 8. Extract optional phone identifier if present
        String accountIdentifier = null;
        Matcher phoneMatcher = MOMO_PHONE_PATTERN.matcher(ascii);
        if (phoneMatcher.find()) {
            accountIdentifier = phoneMatcher.group(1);
        }

        // 9. Build rich description preserving merchant/counterparty + note
        String description;
        if (eventKindLabel != null) {
            description = eventKindLabel + (note != null ? " - " + note : "");
        } else if (counterparty != null && note != null) {
            description = counterparty + " - " + note;
        } else if (counterparty != null) {
            description = counterparty;
        } else if (note != null) {
            description = note;
        } else {
            description = ascii;
        }

        // Preserve original accented text when building final description? Note: keep ascii clean OR preserve note if accented.
        // Keeping original rawMessage snippet in case accented keywords exist alongside ASCII counterparty:
        if (!rawMessage.equals(ascii) && note != null && description.contains(note)) {
            Matcher origNoteMatcher = Pattern.compile("(?:N[ộo]i\\s*dung|L[ờo]i\\s*nh[ắa]n|Ghi\\s*ch[úu])\\s*[:\\-]\\s*([^.\\r\\n]+)", Pattern.CASE_INSENSITIVE)
                    .matcher(rawMessage);
            if (origNoteMatcher.find()) {
                description = description.replace(note, origNoteMatcher.group(1).trim());
            }
        }

        Instant occurredAt = VietnameseBankParsingSupport.extractTimestampOrDefault(rawMessage, receivedAt);

        return Optional.of(new ParsedBalanceEvent(
                PROVIDER_MOMO,
                channel != null ? channel : IngestionChannel.MOBILE_NOTIFICATION,
                accountIdentifier,
                direction,
                amount,
                runningBalance,
                description,
                transId,
                counterparty,
                linkedBankTransfer,
                counterpartyProvider,
                occurredAt
        ));
    }

    private String cleanCounterparty(String raw) {
        if (raw == null) {
            return null;
        }
        // Remove trailing masked phone like "(0987***123)" and trailing phrases like "Ma GD:..."
        String cleaned = raw
                .replaceAll("\\([^)]*\\*{2,}[^)]*\\)", "")
                .replaceAll("\\s+(?:Ma\\s*GD|Loi\\s*nhan|Noi\\s*dung|So\\s*du).*$", "")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}

