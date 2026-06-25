package com.micatechnologies.minecraft.sum.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Locks the loot-injection invariants from TESTING_PLAN.md §4.5 (C7) at the unit level so a
 * future loot-table edit can't silently break them: $200/$500 must NEVER world-gen, small
 * denominations must outweigh large ones, and most rolls must produce no bill at all.
 *
 * <p>The weighting table is private static data, read here via reflection — no Minecraft
 * runtime needed, only the static {@code ENTRIES}/{@code EMPTY_WEIGHT} fields.
 */
class BillsLootInjectorTest {

    private static int[][] entries() throws Exception {
        Field f = BillsLootInjector.class.getDeclaredField("ENTRIES");
        f.setAccessible(true);
        return (int[][]) f.get(null);
    }

    private static int emptyWeight() throws Exception {
        Field f = BillsLootInjector.class.getDeclaredField("EMPTY_WEIGHT");
        f.setAccessible(true);
        return f.getInt(null);
    }

    private static int targetTableCount() throws Exception {
        Field f = BillsLootInjector.class.getDeclaredField("TARGET_TABLES");
        f.setAccessible(true);
        return ((Set<?>) f.get(null)).size();
    }

    @Test
    void neverInjectsTwoHundredOrFiveHundredBills() throws Exception {
        Set<Integer> denoms = new HashSet<>();
        for (int[] e : entries()) {
            denoms.add(e[0]);
        }
        assertTrue(denoms.contains(1) && denoms.contains(100), "small + mid denoms should be injectable");
        assertEquals(false, denoms.contains(200), "$200 must be earned, never world-gen");
        assertEquals(false, denoms.contains(500), "$500 must be earned, never world-gen");
    }

    @Test
    void onlyExpectedDenominationsArePresent() throws Exception {
        Set<Integer> allowed = new HashSet<>();
        for (int d : new int[] {1, 5, 10, 20, 50, 100}) {
            allowed.add(d);
        }
        for (int[] e : entries()) {
            assertTrue(allowed.contains(e[0]), "unexpected denomination injected: " + e[0]);
        }
    }

    @Test
    void smallerDenominationsHaveStrictlyHigherWeight() throws Exception {
        int[][] e = entries();
        for (int i = 0; i < e.length - 1; i++) {
            // ENTRIES are ordered ascending by denomination; weight must fall as value rises.
            assertTrue(e[i][1] > e[i + 1][1],
                "weight for $" + e[i][0] + " (" + e[i][1] + ") should exceed $" + e[i + 1][0]
                    + " (" + e[i + 1][1] + ")");
        }
    }

    @Test
    void countRangesAreSaneAndSmallDenomsStackHigher() throws Exception {
        int[][] e = entries();
        int prevMax = Integer.MAX_VALUE;
        for (int[] row : e) {
            int denom = row[0];
            int min = row[2];
            int max = row[3];
            assertTrue(min >= 1, "$" + denom + " min count must be >= 1");
            assertTrue(max >= min, "$" + denom + " max count must be >= min");
            // Bigger bills shouldn't drop in larger stacks than smaller bills.
            assertTrue(max <= prevMax, "$" + denom + " max stack should not exceed the smaller denom's");
            prevMax = max;
        }
    }

    @Test
    void mostRollsProduceNothing() throws Exception {
        int totalBillWeight = 0;
        for (int[] e : entries()) {
            totalBillWeight += e[1];
        }
        // Empty weight dominates so the typical chest still drops vanilla loot.
        assertTrue(emptyWeight() > totalBillWeight,
            "empty weight (" + emptyWeight() + ") should exceed total bill weight (" + totalBillWeight + ")");
    }

    @Test
    void injectsIntoThirteenVanillaTables() throws Exception {
        // Matches the doc's "13 vanilla chest tables" claim; a change here is worth noticing.
        assertEquals(13, targetTableCount());
    }
}
