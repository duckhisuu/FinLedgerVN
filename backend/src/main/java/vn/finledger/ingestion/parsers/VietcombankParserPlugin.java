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
 * Parser plugin for Vietcombank (VCB) OTT App Push, SMS Banking, and Email alerts.
 */
@Component
@Order(10)
public class VietcombankParserPlugin implements FinancialMessageParserPlugin {

    public static final String PROVIDER_VCB = "VCB";

    private static final Pattern ACCOUNT_PATTERN = Pattern.compile(
            "(?:SO\\s*DU\\s*TK|SD\\s*TK|TK\\s*VCB|TAI\\s*KHOAN(?:\\s+THANH\\s+TOAN)?|TK)\\s*[:.]?\\s*(?:VCB\\s*)?(\\d{8,18})\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SIGNED_CHANGE_PATTERN = Pattern.compile(
            "(?:thay\\s*doi|sd\\s*tk\\s*\\d+|tk\\s*(?:vcb)?\\s*\\d+|so\\s*tien|giao\\s*dich)[^\\r\\n+\\-]{0,40}?([+\\-])\\s*(\\d{1,3}(?:[.,]\\d{3})+|\\d+)\\s*(?:VND|DONG|VNĐ|Đ|D)?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern UNSIGNED_CHANGE_PATTERN = Pattern.compile(
            "([+\\-])\\s*(\\d{1,3}(?:[.,]\\d{3})+|\\d+)\\s*(?:VND|VNĐ|DONG)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern RUNNING_BALANCE_PATTERN = Pattern.compile(
            "(?:so\\s*du(?:\\s+hien\\s+tai|\\s+kha\\s+dung|\\s+sau\\s+gd)?|\\bsd)\\s*[:.]?\\s*(\\d{1,3}(?:[.,]\\d{3})+|\\d+)\\s*(?:VND|VNĐ|DONG)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REF_BLOCK_PATTERN = Pattern.compile(
            "Ref\\s+([^\\r\\n]+)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REF_CODE_MBVCB_PATTERN = Pattern.compile(
            "^(MBVCB\\.\\d+)(?:\\.(\\d{4,10}))?(?:\\.(.+))?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REF_CODE_PREFIX_PATTERN = Pattern.compile(
            "^((?:FT|ECOM|VCB|IBFT)[A-Za-z0-9_-]{4,24})(?:[.\\s]+(.+))?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern NO_REF_DESC_PATTERN = Pattern.compile(
            "(?:Noi\\s*dung(?:\\s*chuyen\\s*tien)?|Mo\\s*ta|Chi\\s*tiet)\\s*[:\\-]\\s*(.+)$",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public String providerCode() {
        return PROVIDER_VCB;
    }

    @Override
    public boolean supports(String senderHint, String subject, String rawMessage, IngestionChannel channel) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return false;
        }
        String combinedAscii = VietnameseBankParsingSupport.normalizeUpper(
                (senderHint != null ? senderHint + " " : "") +
                (subject != null ? subject + " " : "") +
                rawMessage
        );

        boolean hasVcbPattern = ACCOUNT_PATTERN.matcher(combinedAscii).find()
                || combinedAscii.contains("MBVCB.")
                || combinedAscii.contains("VCBDIGIBANK")
                || combinedAscii.contains("TK VCB")
                || combinedAscii.contains("SD TK ");

        if (hasVcbPattern) {
            return true;
        }

        // If message clearly belongs to MoMo ("VI MOMO" / "MA GD:"), do NOT claim it just because VCB is the funding source
        boolean looksLikeMoMo = combinedAscii.contains("VI MOMO")
                || combinedAscii.contains("MA GD:")
                || combinedAscii.contains("MOMO TRUNG TAM");
        if (looksLikeMoMo) {
            return false;
        }

        return senderHint != null && (
                senderHint.toUpperCase(Locale.ROOT).contains("VCB")
                        || senderHint.toUpperCase(Locale.ROOT).contains("VIETCOMBANK")
        );
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

        String ascii = VietnameseBankParsingSupport.normalizeAscii(rawMessage);
        String upper = ascii.toUpperCase(Locale.ROOT);

        // 1. Account identifier
        String accountIdentifier = null;
        Matcher accMatcher = ACCOUNT_PATTERN.matcher(ascii);
        if (accMatcher.find()) {
            accountIdentifier = accMatcher.group(1);
        }

        // 2. Signed balance change (+/- amount)
        Matcher changeMatcher = SIGNED_CHANGE_PATTERN.matcher(ascii);
        if (!changeMatcher.find()) {
            changeMatcher = UNSIGNED_CHANGE_PATTERN.matcher(ascii);
            if (!changeMatcher.find()) {
                return Optional.empty();
            }
        }

        String sign = changeMatcher.group(1);
        TransferDirection direction = "-".equals(sign) ? TransferDirection.OUT : TransferDirection.IN;
        Optional<BigDecimal> amountOpt = VietnameseBankParsingSupport.parseVndAmount(changeMatcher.group(2));
        if (amountOpt.isEmpty() || amountOpt.get().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        BigDecimal amount = amountOpt.get();
        int searchBalanceAfterIndex = changeMatcher.end();

        // 3. Running balance after tx (always look after the delta match so we don't pick up header words)
        BigDecimal runningBalance = null;
        String tailAfterDelta = ascii.substring(Math.min(searchBalanceAfterIndex, ascii.length()));
        Matcher balMatcher = RUNNING_BALANCE_PATTERN.matcher(tailAfterDelta);
        if (balMatcher.find()) {
            runningBalance = VietnameseBankParsingSupport.parseVndAmount(balMatcher.group(1)).orElse(null);
        }

        // 4. Timestamp
        Instant occurredAt = VietnameseBankParsingSupport.extractTimestampOrDefault(rawMessage, receivedAt);

        // 5. Reference code & actual description
        String refCode = null;
        String cleanDesc = null;

        Matcher refBlockMatcher = REF_BLOCK_PATTERN.matcher(ascii);
        if (refBlockMatcher.find()) {
            String refTail = refBlockMatcher.group(1).trim();
            Matcher mbvcbMatcher = REF_CODE_MBVCB_PATTERN.matcher(refTail);
            if (mbvcbMatcher.matches()) {
                refCode = mbvcbMatcher.group(1);
                cleanDesc = mbvcbMatcher.group(3) != null ? mbvcbMatcher.group(3).trim() : refTail;
            } else {
                Matcher prefixMatcher = REF_CODE_PREFIX_PATTERN.matcher(refTail);
                if (prefixMatcher.matches()) {
                    refCode = prefixMatcher.group(1);
                    cleanDesc = prefixMatcher.group(2) != null ? prefixMatcher.group(2).trim() : refTail;
                } else {
                    int spaceOrDot = findFirstRefSeparator(refTail);
                    if (spaceOrDot > 0) {
                        refCode = refTail.substring(0, spaceOrDot);
                        cleanDesc = refTail.substring(spaceOrDot + 1).trim();
                    } else {
                        refCode = refTail;
                        cleanDesc = refTail;
                    }
                }
            }
        } else {
            Matcher noRefDesc = NO_REF_DESC_PATTERN.matcher(ascii);
            if (noRefDesc.find()) {
                cleanDesc = noRefDesc.group(1).trim();
            } else {
                cleanDesc = ascii;
            }
        }

        // Strip trailing boilerplate ".CT tu ..." if present for cleaner display while preserving key text
        if (cleanDesc == null || cleanDesc.isBlank()) {
            cleanDesc = ascii;
        } else {
            String cleanedNoBoiler = cleanDesc.replaceAll("\\.?CT\\s+tu\\s+\\d+.*$", "").trim();
            if (!cleanedNoBoiler.isBlank()) {
                cleanDesc = cleanedNoBoiler;
            }
        }

        // 6. Detect internal MoMo counterparty transfer hint
        String upperDescAndRaw = (upper + " " + cleanDesc.toUpperCase(Locale.ROOT));
        boolean isMoMoTransfer = upperDescAndRaw.contains("MOMO")
                || upperDescAndRaw.contains("M_SERVICE")
                || upperDescAndRaw.contains("MSERVICE");

        String counterpartyProvider = isMoMoTransfer ? "MOMO" : null;
        String counterpartyHint = isMoMoTransfer ? "MOMO" : null;

        return Optional.of(new ParsedBalanceEvent(
                PROVIDER_VCB,
                channel != null ? channel : IngestionChannel.MOBILE_NOTIFICATION,
                accountIdentifier,
                direction,
                amount,
                runningBalance,
                cleanDesc,
                refCode,
                counterpartyHint,
                isMoMoTransfer,
                counterpartyProvider,
                occurredAt
        ));
    }

    private int findFirstRefSeparator(String refTail) {
        int spaceIdx = refTail.indexOf(' ');
        int dotIdx = refTail.indexOf('.');
        if (spaceIdx < 0) return dotIdx;
        if (dotIdx < 0) return spaceIdx;
        return Math.min(spaceIdx, dotIdx);
    }
}

