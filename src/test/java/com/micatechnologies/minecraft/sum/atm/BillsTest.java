package com.micatechnologies.minecraft.sum.atm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the denomination tables in {@link Bills} that don't touch the item registry
 * ({@code allDenominationsHighToLow} and the {@code WITHDRAW_DENOMINATIONS} GUI subset).
 */
class BillsTest {

    @Test
    void allDenominationsAreTheCanonicalEight() {
        assertArrayEquals(new int[] {500, 200, 100, 50, 20, 10, 5, 1}, Bills.allDenominationsHighToLow());
    }

    @Test
    void allDenominationsAreStrictlyDescending() {
        int[] d = Bills.allDenominationsHighToLow();
        for (int i = 0; i < d.length - 1; i++) {
            assertTrue(d[i] > d[i + 1], "denominations must be high-to-low");
        }
    }

    @Test
    void allDenominationsReturnsADefensiveCopy() {
        int[] first = Bills.allDenominationsHighToLow();
        first[0] = -999;
        assertNotSame(first, Bills.allDenominationsHighToLow());
        assertEquals(500, Bills.allDenominationsHighToLow()[0], "internal table must not be mutable via the getter");
    }

    @Test
    void withdrawButtonsExcludeTheEarnedOnlyDenominations() {
        for (int denom : Bills.WITHDRAW_DENOMINATIONS) {
            assertFalse(denom == 200 || denom == 500,
                "ATM withdraw buttons must not offer the earned-only $200/$500 denominations");
        }
    }

    @Test
    void withdrawButtonsAreAscendingAndExpected() {
        assertArrayEquals(new int[] {1, 5, 10, 20, 50, 100}, Bills.WITHDRAW_DENOMINATIONS);
    }
}
