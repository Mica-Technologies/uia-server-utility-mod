package com.micatechnologies.minecraft.sum.omceapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the rounding invariant behind wallet-to-bank transfers.
 *
 * <p>Found in live testing: depositing $354.50 into a whole-unit bank debited the wallet $354.50
 * and credited the bank 355, creating half a unit from nothing. The wallet is the finer-grained
 * side, so a transfer must be snapped <b>down</b> to what the bank can represent, and both sides
 * must then move that same figure.
 *
 * <p>{@code BankService.quantise} is the production implementation; it needs a live server to
 * read the currency scale, so the arithmetic is mirrored here and checked against
 * {@link OmceMoney}, which is what actually converts for the wire.
 */
class OmceMoneyQuantiseTest {

    /** The same floor-with-epsilon rule as {@code BankService.quantise}. */
    private static double quantise(double amount, int digits) {
        double factor = 1.0;
        for (int i = 0; i < digits; i++) {
            factor *= 10.0;
        }
        return Math.floor(amount * factor + 1.0e-9) / factor;
    }

    @Test
    @DisplayName("the bug: a whole-unit bank must not round a deposit up")
    void depositRoundsDown() {
        // $354.50 into a whole-unit bank settles $354; the 50c stays in the wallet.
        assertEquals(354.0, quantise(354.50, 0), 1e-9);
        assertEquals(354L, OmceMoney.toMinorUnits(quantise(354.50, 0), 0));
        // Half-up on the raw amount is what created the extra unit.
        assertEquals(355L, OmceMoney.toMinorUnits(354.50, 0),
            "documents the old behaviour that conjured half a unit");
    }

    @Test
    @DisplayName("an already-representable amount is untouched")
    void exactAmountsSurvive() {
        assertEquals(354.0, quantise(354.0, 0), 1e-9);
        assertEquals(137.0, quantise(137.0, 0), 1e-9);
        assertEquals(12.34, quantise(12.34, 2), 1e-9);
    }

    @Test
    @DisplayName("float noise does not knock a whole number down a unit")
    void toleratesFloatNoise() {
        // 137.0 can arrive as 136.99999999999997 after arithmetic upstream.
        assertEquals(137.0, quantise(136.99999999999997, 0), 1e-9);
        assertEquals(29.0, quantise(28.999999999999996, 0), 1e-9);
    }

    @Test
    @DisplayName("a cents-scale bank keeps cents")
    void centsScaleIsUnaffected() {
        assertEquals(354.50, quantise(354.50, 2), 1e-9);
        assertEquals(35450L, OmceMoney.toMinorUnits(quantise(354.50, 2), 2));
    }

    @Test
    @DisplayName("sub-unit amounts quantise to zero so the caller can refuse them")
    void subUnitAmountsCollapse() {
        // Depositing 50c into a whole-unit bank must move nothing at all, rather
        // than rounding up to a unit the wallet never gave up.
        assertEquals(0.0, quantise(0.50, 0), 1e-9);
        assertEquals(0.0, quantise(0.99, 0), 1e-9);
    }

    @Test
    @DisplayName("value is conserved: what the bank takes is exactly representable")
    void conservesValueAcrossScales() {
        for (int digits : new int[] { 0, 2 }) {
            for (int cents = 1; cents <= 5000; cents++) {
                double amount = cents / 100.0;
                double settled = quantise(amount, digits);
                assertTrue(settled <= amount + 1e-9,
                    "quantise must never round up: " + amount + " at " + digits);
                assertTrue(OmceMoney.isExactlyRepresentable(settled, digits),
                    "settled amount must be exact: " + settled + " at " + digits);
                // The remainder the wallet keeps is always less than one unit.
                double unit = digits == 0 ? 1.0 : 0.01;
                assertTrue(amount - settled < unit + 1e-9,
                    "remainder too large for " + amount + " at " + digits);
            }
        }
    }
}
