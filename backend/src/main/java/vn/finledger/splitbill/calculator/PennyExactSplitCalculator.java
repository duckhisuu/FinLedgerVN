package vn.finledger.splitbill.calculator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.finledger.splitbill.domain.SplitStrategy;
import vn.finledger.splitbill.exception.InvalidSplitAllocationException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;

/**
 * Penny-Exact Expense Split Calculator.
 * Implements deterministic largest-remainder penny allocation guaranteeing
 * that SUM(shareAmount) equals totalAmount with zero fractional drift.
 */
@Slf4j
@Component
public class PennyExactSplitCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * Calculates penny-exact allocations for a group expense across members.
     *
     * @param totalAmount total expenditure amount
     * @param strategy    split strategy (EQUAL, EXACT, PERCENTAGE, SHARES)
     * @param inputs      member participation list with optional weights
     * @return list of calculated shares strictly matching totalAmount
     */
    public List<CalculatedShare> calculate(
            BigDecimal totalAmount,
            SplitStrategy strategy,
            List<SplitShareInput> inputs) {

        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidSplitAllocationException("Total amount must be strictly greater than zero");
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new InvalidSplitAllocationException("Participant list cannot be empty");
        }
        if (strategy == null) {
            throw new InvalidSplitAllocationException("Split strategy cannot be null");
        }

        BigDecimal normalizedTotal = totalAmount.setScale(4, RoundingMode.HALF_EVEN);

        List<CalculatedShare> results = switch (strategy) {
            case EQUAL -> calculateEqual(normalizedTotal, inputs);
            case EXACT -> calculateExact(normalizedTotal, inputs);
            case PERCENTAGE -> calculatePercentage(normalizedTotal, inputs);
            case SHARES -> calculateShares(normalizedTotal, inputs);
        };

        // Assert invariant: SUM(share_amount) == totalAmount
        BigDecimal sum = results.stream()
                .map(CalculatedShare::shareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_EVEN);

        if (sum.compareTo(normalizedTotal) != 0) {
            throw new InvalidSplitAllocationException(
                    String.format("Internal allocation error: sum of shares (%s) does not match total amount (%s)",
                            sum, normalizedTotal));
        }

        return results;
    }

    private List<CalculatedShare> calculateEqual(BigDecimal totalAmount, List<SplitShareInput> inputs) {
        int n = inputs.size();
        // Deterministic sort by memberId ASC
        List<SplitShareInput> sorted = new ArrayList<>(inputs);
        sorted.sort(Comparator.comparing(SplitShareInput::memberId));

        int scale = Math.max(0, totalAmount.stripTrailingZeros().scale());
        BigInteger totalUnits = totalAmount.scaleByPowerOfTen(scale).toBigIntegerExact();

        BigInteger count = BigInteger.valueOf(n);
        BigInteger baseUnits = totalUnits.divide(count);
        int remainder = totalUnits.remainder(count).intValue();

        List<CalculatedShare> shares = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BigInteger units = (i < remainder) ? baseUnits.add(BigInteger.ONE) : baseUnits;
            BigDecimal shareAmount = new BigDecimal(units)
                    .scaleByPowerOfTen(-scale)
                    .setScale(4, RoundingMode.HALF_EVEN);

            shares.add(new CalculatedShare(sorted.get(i).memberId(), shareAmount, BigDecimal.ONE));
        }

        return shares;
    }

    private List<CalculatedShare> calculateExact(BigDecimal totalAmount, List<SplitShareInput> inputs) {
        BigDecimal sum = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);
        List<CalculatedShare> shares = new ArrayList<>();

        for (SplitShareInput input : inputs) {
            if (input.amountOrWeight() == null || input.amountOrWeight().compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidSplitAllocationException(
                        "Exact share amount for member " + input.memberId() + " cannot be null or negative");
            }
            BigDecimal amt = input.amountOrWeight().setScale(4, RoundingMode.HALF_EVEN);
            sum = sum.add(amt);
            shares.add(new CalculatedShare(input.memberId(), amt, amt));
        }

        if (sum.compareTo(totalAmount) != 0) {
            throw new InvalidSplitAllocationException(
                    String.format("Sum of exact shares (%s) does not match total amount (%s)", sum, totalAmount));
        }

        return shares;
    }

    private List<CalculatedShare> calculatePercentage(BigDecimal totalAmount, List<SplitShareInput> inputs) {
        BigDecimal totalPercent = BigDecimal.ZERO;
        for (SplitShareInput input : inputs) {
            if (input.amountOrWeight() == null || input.amountOrWeight().compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidSplitAllocationException("Percentage cannot be negative or null");
            }
            totalPercent = totalPercent.add(input.amountOrWeight());
        }

        if (totalPercent.compareTo(HUNDRED) != 0) {
            throw new InvalidSplitAllocationException(
                    "Total percentage must equal exactly 100%, but was " + totalPercent);
        }

        return distributeByWeights(totalAmount, inputs, HUNDRED);
    }

    private List<CalculatedShare> calculateShares(BigDecimal totalAmount, List<SplitShareInput> inputs) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (SplitShareInput input : inputs) {
            if (input.amountOrWeight() == null || input.amountOrWeight().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidSplitAllocationException("Share weight must be strictly positive");
            }
            totalWeight = totalWeight.add(input.amountOrWeight());
        }

        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidSplitAllocationException("Total share weight must be positive");
        }

        return distributeByWeights(totalAmount, inputs, totalWeight);
    }

    /**
     * Largest Remainder Method (Hare-Niemeyer / Hamilton method) for proportional allocations.
     */
    private List<CalculatedShare> distributeByWeights(
            BigDecimal totalAmount,
            List<SplitShareInput> inputs,
            BigDecimal totalWeight) {

        int scale = Math.max(0, totalAmount.stripTrailingZeros().scale());
        BigInteger totalUnits = totalAmount.scaleByPowerOfTen(scale).toBigIntegerExact();

        record MemberAllocation(
                UUID memberId,
                BigDecimal rawWeight,
                BigInteger baseUnits,
                BigDecimal fraction
        ) {}

        List<MemberAllocation> allocations = new ArrayList<>();
        BigInteger allocatedUnits = BigInteger.ZERO;

        for (SplitShareInput input : inputs) {
            BigDecimal exactUnits = new BigDecimal(totalUnits)
                    .multiply(input.amountOrWeight())
                    .divide(totalWeight, 12, RoundingMode.HALF_EVEN);

            BigInteger base = exactUnits.toBigInteger();
            BigDecimal fraction = exactUnits.subtract(new BigDecimal(base));

            allocatedUnits = allocatedUnits.add(base);
            allocations.add(new MemberAllocation(input.memberId(), input.amountOrWeight(), base, fraction));
        }

        int remainder = totalUnits.subtract(allocatedUnits).intValue();

        // Sort by fraction DESC, tie-break by memberId ASC
        allocations.sort((a, b) -> {
            int cmp = b.fraction().compareTo(a.fraction());
            if (cmp != 0) return cmp;
            return a.memberId().compareTo(b.memberId());
        });

        List<CalculatedShare> result = new ArrayList<>();
        for (int i = 0; i < allocations.size(); i++) {
            MemberAllocation alloc = allocations.get(i);
            BigInteger units = (i < remainder) ? alloc.baseUnits().add(BigInteger.ONE) : alloc.baseUnits();
            BigDecimal finalAmount = new BigDecimal(units)
                    .scaleByPowerOfTen(-scale)
                    .setScale(4, RoundingMode.HALF_EVEN);

            result.add(new CalculatedShare(alloc.memberId(), finalAmount, alloc.rawWeight()));
        }

        return result;
    }
}
