package vn.finledger.splitbill.algorithm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Min-Cash-Flow Debt Simplification Graph Engine.
 * Reduces an arbitrary directed graph of multi-party debts to a minimal set
 * of at most N - 1 transactions using greedy dual Max-Heaps.
 * Guarantees conservation of net flow and deterministic, acyclic settlement.
 */
@Slf4j
@Component
public class DebtSimplificationEngine {

    private record BalanceNode(UUID memberId, BigDecimal amount) {}

    /**
     * Simplifies raw bilateral debt edges into an optimal transfer schedule.
     */
    public List<SimplifiedTransfer> simplify(List<RawDebtEdge> rawDebts) {
        if (rawDebts == null || rawDebts.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, BigDecimal> netBalances = new HashMap<>();
        for (RawDebtEdge edge : rawDebts) {
            if (edge.amount() == null || edge.amount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (edge.debtorId().equals(edge.creditorId())) {
                continue;
            }
            // Debtor owes money -> negative net balance
            netBalances.merge(edge.debtorId(), edge.amount().negate(), BigDecimal::add);
            // Creditor is owed money -> positive net balance
            netBalances.merge(edge.creditorId(), edge.amount(), BigDecimal::add);
        }

        return simplifyFromNetBalances(netBalances);
    }

    /**
     * Simplifies debts directly from a map of member net balances.
     * NetBalance > 0: Creditor needs to receive.
     * NetBalance < 0: Debtor needs to pay.
     */
    public List<SimplifiedTransfer> simplifyFromNetBalances(Map<UUID, BigDecimal> netBalances) {
        if (netBalances == null || netBalances.isEmpty()) {
            return Collections.emptyList();
        }

        // Validate algebraic conservation invariant: SUM(netBalance) == 0.0000
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal b : netBalances.values()) {
            if (b != null) {
                sum = sum.add(b);
            }
        }
        sum = sum.setScale(4, RoundingMode.HALF_EVEN);

        if (sum.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException(
                    "Conservation of flow violated: sum of net balances must equal 0.0000, but was " + sum);
        }

        // Deterministic Max-Heap comparator: Amount DESC, then UUID ASC
        Comparator<BalanceNode> maxHeapComparator = (a, b) -> {
            int cmp = b.amount().compareTo(a.amount());
            if (cmp != 0) return cmp;
            return a.memberId().compareTo(b.memberId());
        };

        PriorityQueue<BalanceNode> creditorsHeap = new PriorityQueue<>(maxHeapComparator);
        PriorityQueue<BalanceNode> debtorsHeap = new PriorityQueue<>(maxHeapComparator);

        for (Map.Entry<UUID, BigDecimal> entry : netBalances.entrySet()) {
            BigDecimal bal = entry.getValue() != null
                    ? entry.getValue().setScale(4, RoundingMode.HALF_EVEN)
                    : BigDecimal.ZERO.setScale(4);

            if (bal.compareTo(BigDecimal.ZERO) > 0) {
                creditorsHeap.offer(new BalanceNode(entry.getKey(), bal));
            } else if (bal.compareTo(BigDecimal.ZERO) < 0) {
                debtorsHeap.offer(new BalanceNode(entry.getKey(), bal.abs()));
            }
        }

        List<SimplifiedTransfer> transfers = new ArrayList<>();

        while (!creditorsHeap.isEmpty() && !debtorsHeap.isEmpty()) {
            BalanceNode creditor = creditorsHeap.poll();
            BalanceNode debtor = debtorsHeap.poll();

            BigDecimal transferAmount = creditor.amount().min(debtor.amount());
            if (transferAmount.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            transfers.add(new SimplifiedTransfer(
                    debtor.memberId(),
                    creditor.memberId(),
                    transferAmount.setScale(4, RoundingMode.HALF_EVEN),
                    "Chuyển khoản rút gọn qua bù trừ công nợ nhóm"
            ));

            BigDecimal credRemaining = creditor.amount().subtract(transferAmount)
                    .setScale(4, RoundingMode.HALF_EVEN);
            if (credRemaining.compareTo(BigDecimal.ZERO) > 0) {
                creditorsHeap.offer(new BalanceNode(creditor.memberId(), credRemaining));
            }

            BigDecimal debtRemaining = debtor.amount().subtract(transferAmount)
                    .setScale(4, RoundingMode.HALF_EVEN);
            if (debtRemaining.compareTo(BigDecimal.ZERO) > 0) {
                debtorsHeap.offer(new BalanceNode(debtor.memberId(), debtRemaining));
            }
        }

        log.debug("Simplified {} non-zero members into {} minimal transfers",
                netBalances.size(), transfers.size());
        return transfers;
    }
}
