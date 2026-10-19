package vn.finledger.splitbill.algorithm;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import vn.finledger.splitbill.calculator.CalculatedShare;
import vn.finledger.splitbill.calculator.PennyExactSplitCalculator;
import vn.finledger.splitbill.calculator.SplitShareInput;
import vn.finledger.splitbill.domain.SplitStrategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class DebtSimplificationPropertyTest {

    private final DebtSimplificationEngine engine = new DebtSimplificationEngine();
    private final PennyExactSplitCalculator calculator = new PennyExactSplitCalculator();

    record ExpenseScenario(List<UUID> members, List<RawDebtEdge> debts) {}

    @Provide
    Arbitrary<ExpenseScenario> randomExpenseScenarios() {
        return Arbitraries.integers().between(2, 15).flatMap(numMembers -> {
            List<UUID> memberIds = new ArrayList<>();
            for (int i = 0; i < numMembers; i++) {
                memberIds.add(UUID.randomUUID());
            }

            Arbitrary<UUID> memberArb = Arbitraries.of(memberIds);
            Arbitrary<BigDecimal> amountArb = Arbitraries.bigDecimals()
                    .between(new BigDecimal("1000"), new BigDecimal("50000000"))
                    .map(b -> b.setScale(0, RoundingMode.HALF_EVEN).setScale(4));

            Arbitrary<RawDebtEdge> edgeArb = Combinators.combine(memberArb, memberArb, amountArb)
                    .as((debtor, creditor, amt) -> new RawDebtEdge(debtor, creditor, amt))
                    .filter(e -> !e.debtorId().equals(e.creditorId()));

            return edgeArb.list().ofMinSize(1).ofMaxSize(50)
                    .map(debts -> new ExpenseScenario(memberIds, debts));
        });
    }

    /**
     * Proves 3 Mathematical Invariants across 500 random property scenarios:
     * 1. Conservation of Flow (each member's net cash flow matches initial balance)
     * 2. Upper Bound N - 1 (simplified transfers <= nonZeroMembers - 1)
     * 3. Acyclic Bipartite Property (no member is both a sender and receiver)
     */
    @Property(tries = 500)
    void proveThreeDebtSimplificationTheorems(@ForAll("randomExpenseScenarios") ExpenseScenario scenario) {
        Map<UUID, BigDecimal> initialNetBalances = new HashMap<>();
        for (UUID m : scenario.members()) {
            initialNetBalances.put(m, BigDecimal.ZERO.setScale(4));
        }

        for (RawDebtEdge edge : scenario.debts()) {
            initialNetBalances.merge(edge.debtorId(), edge.amount().negate(), BigDecimal::add);
            initialNetBalances.merge(edge.creditorId(), edge.amount(), BigDecimal::add);
        }

        List<SimplifiedTransfer> transfers = engine.simplify(scenario.debts());

        // Theorem 1: Conservation of Flow
        for (UUID m : scenario.members()) {
            BigDecimal initialNet = initialNetBalances.get(m).setScale(4, RoundingMode.HALF_EVEN);

            BigDecimal received = transfers.stream()
                    .filter(t -> t.toMemberId().equals(m))
                    .map(SimplifiedTransfer::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal sent = transfers.stream()
                    .filter(t -> t.fromMemberId().equals(m))
                    .map(SimplifiedTransfer::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal netFlow = received.subtract(sent).setScale(4, RoundingMode.HALF_EVEN);
            assertThat(netFlow).isEqualByComparingTo(initialNet);
        }

        // Theorem 2: Upper Bound K - 1
        long nonZeroCount = initialNetBalances.values().stream()
                .filter(b -> b.setScale(4, RoundingMode.HALF_EVEN).compareTo(BigDecimal.ZERO) != 0)
                .count();

        if (nonZeroCount <= 1) {
            assertThat(transfers).isEmpty();
        } else {
            assertThat(transfers.size()).isLessThanOrEqualTo((int) nonZeroCount - 1);
        }

        // Theorem 3: Acyclic Bipartite Property
        Set<UUID> senders = transfers.stream()
                .map(SimplifiedTransfer::fromMemberId)
                .collect(Collectors.toSet());
        Set<UUID> receivers = transfers.stream()
                .map(SimplifiedTransfer::toMemberId)
                .collect(Collectors.toSet());

        Set<UUID> intersection = new HashSet<>(senders);
        intersection.retainAll(receivers);
        assertThat(intersection)
                .as("No member can be both a sender and receiver in simplified graph")
                .isEmpty();
    }

    /**
     * Proves Penny-Exact Allocation: splitting any arbitrary random positive amount
     * across N participants strictly sums to the original amount.
     */
    @Property(tries = 500)
    void provePennyExactAllocationSumsStrictlyToTotal(
            @ForAll @IntRange(min = 1, max = 20) int numMembers,
            @ForAll @IntRange(min = 1, max = 100_000_000) int totalAmountInt
    ) {
        BigDecimal totalAmount = BigDecimal.valueOf(totalAmountInt).setScale(4);
        List<SplitShareInput> inputs = new ArrayList<>();
        for (int i = 0; i < numMembers; i++) {
            inputs.add(new SplitShareInput(UUID.randomUUID()));
        }

        List<CalculatedShare> shares = calculator.calculate(totalAmount, SplitStrategy.EQUAL, inputs);

        assertThat(shares).hasSize(numMembers);

        BigDecimal sum = shares.stream()
                .map(CalculatedShare::shareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_EVEN);

        assertThat(sum).isEqualByComparingTo(totalAmount);
    }
}
