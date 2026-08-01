package com.micatechnologies.minecraft.sum.atm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link Bills#breakIntoBills} moved here from the shop so the ATM could reuse it for returning a
 * refused deposit. That made it a money-handling path in its own right: if it under-counts, a
 * player's bills are destroyed rather than returned.
 */
class BillsBreakdownTest {

    /** Total dollar value of a decomposition, for conservation checks. */
    private static int valueOf(List<int[]> pairs) {
        int total = 0;
        for (int[] pair : pairs) {
            total += pair[0] * pair[1];
        }
        return total;
    }

    @Test
    @DisplayName("zero and negative amounts produce nothing")
    void emptyForNonPositive() {
        assertTrue(Bills.breakIntoBills(0.0).isEmpty());
        assertTrue(Bills.breakIntoBills(-50.0).isEmpty());
    }

    @Test
    @DisplayName("an exact denomination yields one pair")
    void exactDenomination() {
        List<int[]> pairs = Bills.breakIntoBills(500.0);
        assertEquals(1, pairs.size());
        assertEquals(500, pairs.get(0)[0]);
        assertEquals(1, pairs.get(0)[1]);
    }

    @Test
    @DisplayName("decomposition is greedy largest-first")
    void greedyLargestFirst() {
        List<int[]> pairs = Bills.breakIntoBills(686.0);
        // 686 = 500 + 100 + 50 + 20 + 10 + 5 + 1
        assertEquals(686, valueOf(pairs));
        assertEquals(500, pairs.get(0)[0], "largest denomination must come first");
        int previous = Integer.MAX_VALUE;
        for (int[] pair : pairs) {
            assertTrue(pair[0] <= previous, "denominations must be non-increasing");
            previous = pair[0];
        }
    }

    @Test
    @DisplayName("value is conserved across a range of amounts")
    void conservesValue() {
        for (int amount = 1; amount <= 2000; amount++) {
            assertEquals(amount, valueOf(Bills.breakIntoBills(amount)),
                "value lost decomposing $" + amount);
        }
    }

    @Test
    @DisplayName("sub-dollar remainders are floored away, never rounded up")
    void floorsBelowOneDollar() {
        assertTrue(Bills.breakIntoBills(0.99).isEmpty());
        assertEquals(5, valueOf(Bills.breakIntoBills(5.99)),
            "there is no sub-dollar bill, so cents are dropped rather than inflated");
    }

    @Test
    @DisplayName("no pair exceeds a full stack, so each maps to one ItemStack")
    void stacksAreCapped() {
        // The ATM refund path relies on this to emit one ItemStack per pair.
        for (int[] pair : Bills.breakIntoBills(100_000.0)) {
            assertTrue(pair[1] >= 1 && pair[1] <= 64,
                "count " + pair[1] + " is not a valid stack size");
        }
        assertEquals(100_000, valueOf(Bills.breakIntoBills(100_000.0)));
    }

    @Test
    @DisplayName("a large amount splits into multiple capped stacks of the same denomination")
    void splitsIntoMultipleStacks() {
        // $50,000 is 100 x $500, which cannot fit in one 64-item stack.
        List<int[]> pairs = Bills.breakIntoBills(50_000.0);
        assertEquals(2, pairs.size());
        assertEquals(500, pairs.get(0)[0]);
        assertEquals(64, pairs.get(0)[1]);
        assertEquals(36, pairs.get(1)[1]);
        assertEquals(50_000, valueOf(pairs));
    }
}
