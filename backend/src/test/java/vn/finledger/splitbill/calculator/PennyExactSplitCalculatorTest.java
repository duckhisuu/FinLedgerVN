package vn.finledger.splitbill.calculator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.finledger.splitbill.domain.SplitStrategy;
import vn.finledger.splitbill.exception.InvalidSplitAllocationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PennyExactSplitCalculatorTest {

    private final PennyExactSplitCalculator calculator = new PennyExactSplitCalculator();

    @Test
    @DisplayName("EQUAL strategy: 100.000 split across 3 people gives deterministic [33334, 33333, 33333]")
    void testEqualSplitThreePeople() {
        UUID u1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID u2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID u3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

        List<SplitShareInput> inputs = List.of(
                new SplitShareInput(u2),
                new SplitShareInput(u1),
                new SplitShareInput(u3)
        );

        List<CalculatedShare> shares = calculator.calculate(
                new BigDecimal("100000"), SplitStrategy.EQUAL, inputs);

        assertThat(shares).hasSize(3);

        // Deterministic: u1 gets 33334, u2 gets 33333, u3 gets 33333
        CalculatedShare s1 = shares.stream().filter(s -> s.memberId().equals(u1)).findFirst().orElseThrow();
        CalculatedShare s2 = shares.stream().filter(s -> s.memberId().equals(u2)).findFirst().orElseThrow();
        CalculatedShare s3 = shares.stream().filter(s -> s.memberId().equals(u3)).findFirst().orElseThrow();

        assertThat(s1.shareAmount()).isEqualByComparingTo(new BigDecimal("33334.0000"));
        assertThat(s2.shareAmount()).isEqualByComparingTo(new BigDecimal("33333.0000"));
        assertThat(s3.shareAmount()).isEqualByComparingTo(new BigDecimal("33333.0000"));

        BigDecimal sum = shares.stream()
                .map(CalculatedShare::shareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(new BigDecimal("100000.0000"));
    }

    @Test
    @DisplayName("EXACT strategy: validates sum matches totalAmount or throws exception")
    void testExactSplit() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        List<SplitShareInput> validInputs = List.of(
                new SplitShareInput(u1, new BigDecimal("60000")),
                new SplitShareInput(u2, new BigDecimal("40000"))
        );

        List<CalculatedShare> shares = calculator.calculate(
                new BigDecimal("100000"), SplitStrategy.EXACT, validInputs);
        assertThat(shares).hasSize(2);

        List<SplitShareInput> invalidInputs = List.of(
                new SplitShareInput(u1, new BigDecimal("60000")),
                new SplitShareInput(u2, new BigDecimal("35000"))
        );

        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("100000"), SplitStrategy.EXACT, invalidInputs))
                .isInstanceOf(InvalidSplitAllocationException.class)
                .hasMessageContaining("does not match total amount");
    }

    @Test
    @DisplayName("PERCENTAGE strategy: validates sum is 100% and uses largest remainder for penny-exact split")
    void testPercentageSplit() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();

        // 33.33% + 33.33% + 33.34% = 100.00%
        List<SplitShareInput> inputs = List.of(
                new SplitShareInput(u1, new BigDecimal("33.33")),
                new SplitShareInput(u2, new BigDecimal("33.33")),
                new SplitShareInput(u3, new BigDecimal("33.34"))
        );

        List<CalculatedShare> shares = calculator.calculate(
                new BigDecimal("100000"), SplitStrategy.PERCENTAGE, inputs);

        BigDecimal sum = shares.stream()
                .map(CalculatedShare::shareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(new BigDecimal("100000.0000"));

        // Invalid percentage
        List<SplitShareInput> invalidInputs = List.of(
                new SplitShareInput(u1, new BigDecimal("50")),
                new SplitShareInput(u2, new BigDecimal("40"))
        );
        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("100000"), SplitStrategy.PERCENTAGE, invalidInputs))
                .isInstanceOf(InvalidSplitAllocationException.class)
                .hasMessageContaining("must equal exactly 100%");
    }

    @Test
    @DisplayName("SHARES strategy: proportional weight allocation strictly sums to total")
    void testSharesSplit() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();

        List<SplitShareInput> inputs = List.of(
                new SplitShareInput(u1, new BigDecimal("2")),
                new SplitShareInput(u2, new BigDecimal("1")),
                new SplitShareInput(u3, new BigDecimal("1"))
        );

        List<CalculatedShare> shares = calculator.calculate(
                new BigDecimal("100000"), SplitStrategy.SHARES, inputs);

        BigDecimal sum = shares.stream()
                .map(CalculatedShare::shareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(new BigDecimal("100000.0000"));
    }
}
