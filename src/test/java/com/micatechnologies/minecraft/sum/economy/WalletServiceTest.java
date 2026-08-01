package com.micatechnologies.minecraft.sum.economy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The bill-breaking arithmetic behind {@link WalletService#spend}.
 *
 * <p>A wallet is an invisible balance plus carried notes, so spending has to decide how much of
 * each to use. Getting it wrong either destroys value (over-consuming notes) or lets a player buy
 * something they cannot afford.
 */
class WalletServiceTest {

    /** No notes carried. */
    private static final int[] NONE = {};

    @Test
    @DisplayName("a purchase covered by the invisible balance breaks no notes")
    void balanceCoversIt() {
        long[] plan = WalletService.planSpend(50.0, 100.0, new int[] { 100 });
        assertArrayEquals(new long[] { 0L, 0L }, plan, "no notes should be touched");
    }

    @Test
    @DisplayName("an exactly-covered purchase breaks no notes")
    void exactBalance() {
        assertArrayEquals(new long[] { 0L, 0L }, WalletService.planSpend(100.0, 100.0, NONE));
    }

    @Test
    @DisplayName("a shortfall breaks the smallest notes first")
    void smallestNotesFirst() {
        // $30 short with $1, $5, $10 and $100 available: the first three cover it, not the $100.
        long[] plan = WalletService.planSpend(30.0, 0.0, new int[] { 100, 10, 5, 1, 20 });
        assertEquals(36L, plan[0], "1 + 5 + 10 + 20 covers 30 without reaching the $100");
        assertEquals(600L, plan[1], "change is $6.00 in cents");
    }

    @Test
    @DisplayName("a single large note is broken when nothing smaller will do")
    void breaksALargeNote() {
        long[] plan = WalletService.planSpend(120.0, 50.0, new int[] { 100 });
        assertEquals(100L, plan[0], "the $100 note is consumed");
        assertEquals(3000L, plan[1], "shortfall is $70, so $30 comes back as change");
    }

    @Test
    @DisplayName("change plus the amount spent always equals what the wallet gave up")
    void conservesValue() {
        for (int cents = 1; cents <= 30000; cents += 7) {
            double amount = cents / 100.0;
            double invisible = 12.34;
            long[] plan = WalletService.planSpend(amount, invisible, new int[] { 1, 5, 10, 20, 50, 100, 100 });
            if (plan == null) {
                continue;
            }
            double shortfall = Math.max(0.0, amount - invisible);
            long expectedChange = Math.round((plan[0] - shortfall) * 100.0);
            assertEquals(expectedChange, plan[1], "change wrong for $" + amount);
            assertTrue(plan[1] >= 0L, "change must never be negative for $" + amount);
        }
    }

    @Test
    @DisplayName("an unaffordable purchase plans nothing at all")
    void cannotAfford() {
        assertNull(WalletService.planSpend(500.0, 10.0, new int[] { 20, 5 }));
        assertNull(WalletService.planSpend(1.0, 0.0, NONE));
    }

    @Test
    @DisplayName("a free or negative amount is a no-op")
    void nonPositiveAmount() {
        assertArrayEquals(new long[] { 0L, 0L }, WalletService.planSpend(0.0, 0.0, NONE));
        assertArrayEquals(new long[] { 0L, 0L }, WalletService.planSpend(-5.0, 0.0, NONE));
    }

    @Test
    @DisplayName("notes alone can cover a purchase with an empty balance")
    void notesOnly() {
        long[] plan = WalletService.planSpend(15.0, 0.0, new int[] { 20 });
        assertEquals(20L, plan[0]);
        assertEquals(500L, plan[1], "$5.00 change");
    }

    @Test
    @DisplayName("cents in the price are covered by whole notes with change back")
    void fractionalPrice() {
        long[] plan = WalletService.planSpend(12.60, 0.0, new int[] { 1, 5, 10 });
        // Smallest-first: 1 + 5 + 10 = 16 covers 12.60.
        assertEquals(16L, plan[0]);
        assertEquals(340L, plan[1], "$3.40 change");
    }
}
