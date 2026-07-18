package com.micatechnologies.minecraft.sum.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the pure money arithmetic extracted from {@link MoneyTransfer#transfer}: the half-up
 * {@code roundToCents} and the {@code computeAmounts} validation/fee/clamp chain. The live
 * {@code transfer()} adds an {@code EconomyBridge} charge/credit around this math, but every
 * numeric decision (round, reject non-positive/non-finite, fee round, fee clamp) lives here.
 */
class MoneyTransferTest {

    private static final double EPS = 1e-9;

    // --- roundToCents (half-up on cleanly-representable inputs) ---

    @Test
    void roundToCentsRoundsToNearestCent() {
        assertEquals(1.12, MoneyTransfer.roundToCents(1.124), EPS);
        assertEquals(1.13, MoneyTransfer.roundToCents(1.126), EPS);
        assertEquals(5.00, MoneyTransfer.roundToCents(5.0), EPS);
    }

    @Test
    void roundToCentsHalfRoundsUp() {
        // 1.125 and 2.625 are exact in binary, so the +0.5 half-up rule is unambiguous.
        assertEquals(1.13, MoneyTransfer.roundToCents(1.125), EPS);
        assertEquals(2.63, MoneyTransfer.roundToCents(2.625), EPS);
    }

    @Test
    void roundToCentsUsesFloorOnNegatives() {
        // Documents that the half-up floor biases negatives toward positive infinity.
        // (transfer() rejects negatives before this matters, but pin the raw behavior.)
        assertEquals(-1.12, MoneyTransfer.roundToCents(-1.125), EPS);
    }

    // --- computeAmounts ---

    @Test
    void computeAmountsNoFee() {
        MoneyTransfer.Result r = MoneyTransfer.computeAmounts(100.0, 0.0);
        assertTrue(r.ok);
        assertEquals(100.0, r.amount, EPS);
        assertEquals(100.0, r.credited, EPS);
        assertEquals(0.0, r.fee, EPS);
    }

    @Test
    void computeAmountsAppliesRoundedFee() {
        MoneyTransfer.Result r = MoneyTransfer.computeAmounts(100.0, 5.0);
        assertTrue(r.ok);
        assertEquals(100.0, r.amount, EPS);
        assertEquals(5.0, r.fee, EPS);
        assertEquals(95.0, r.credited, EPS);
    }

    @Test
    void computeAmountsClampsFeeToAmount() {
        // A >100% fee can never exceed the amount; the recipient just receives nothing.
        MoneyTransfer.Result r = MoneyTransfer.computeAmounts(100.0, 150.0);
        assertTrue(r.ok);
        assertEquals(100.0, r.fee, EPS);
        assertEquals(0.0, r.credited, EPS);
    }

    @Test
    void computeAmountsSubCentFeeRoundsToZero() {
        // amount rounds to $0.01; a 10% fee of that is $0.001 → rounds to $0.00, credited $0.01.
        MoneyTransfer.Result r = MoneyTransfer.computeAmounts(0.011, 10.0);
        assertTrue(r.ok);
        assertEquals(0.01, r.amount, EPS);
        assertEquals(0.0, r.fee, EPS);
        assertEquals(0.01, r.credited, EPS);
    }

    @Test
    void computeAmountsRejectsNonPositive() {
        assertFalse(MoneyTransfer.computeAmounts(0.0, 5.0).ok);
        assertFalse(MoneyTransfer.computeAmounts(-10.0, 5.0).ok);
        // Rounds below a cent → treated as zero → rejected.
        assertFalse(MoneyTransfer.computeAmounts(0.004, 5.0).ok);
    }

    @Test
    void computeAmountsRejectsNonFinite() {
        assertFalse(MoneyTransfer.computeAmounts(Double.NaN, 5.0).ok);
        assertFalse(MoneyTransfer.computeAmounts(Double.POSITIVE_INFINITY, 5.0).ok);
        assertFalse(MoneyTransfer.computeAmounts(Double.NEGATIVE_INFINITY, 5.0).ok);
    }
}
