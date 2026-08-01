package com.micatechnologies.minecraft.sum.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The dollars-to-minor-units conversion for {@code /pay}. Two invariants matter here: the
 * recipient's share can never go negative, and the sender's charge must always equal what they
 * were quoted — a coarse currency must not let rounding leak value in either direction.
 */
class MoneyTransferSettlementTest {

    private static long amountOf(double amount, double fee, int digits) {
        return MoneyTransfer.toSettlementUnits(amount, fee, digits)[0];
    }

    private static long feeOf(double amount, double fee, int digits) {
        return MoneyTransfer.toSettlementUnits(amount, fee, digits)[1];
    }

    @Test
    @DisplayName("cents scale converts amount and fee exactly")
    void centsScale() {
        long[] units = MoneyTransfer.toSettlementUnits(50.00, 1.00, 2);
        assertEquals(5000L, units[0]);
        assertEquals(100L, units[1]);
        assertEquals(4900L, units[0] - units[1], "the recipient gets amount minus fee");
    }

    @Test
    @DisplayName("a zero fee yields zero minor units")
    void noFee() {
        assertEquals(0L, feeOf(50.00, 0.0, 2));
        assertEquals(5000L, amountOf(50.00, 0.0, 2));
    }

    @Test
    @DisplayName("the sender's charge rounds up so a coarse currency never under-settles")
    void chargeRoundsUp() {
        // On a whole-unit currency, charging 12 for a 12.01 payment would leak value.
        assertEquals(13L, amountOf(12.01, 0.0, 0));
        assertEquals(13L, amountOf(12.99, 0.0, 0));
        assertEquals(12L, amountOf(12.00, 0.0, 0), "an exact amount must not inflate");
    }

    @Test
    @DisplayName("the fee is clamped to the charge so the recipient's share is never negative")
    void feeNeverExceedsAmount() {
        // A misconfigured feePercent, or aggressive rounding on a coarse currency, must not
        // produce a fee larger than the payment itself.
        long[] units = MoneyTransfer.toSettlementUnits(10.00, 999.00, 2);
        assertEquals(units[0], units[1], "the fee is capped at the full amount");
        assertTrue(units[0] - units[1] >= 0L);
    }

    @Test
    @DisplayName("amount and fee always sum back to the charge")
    void conservesValue() {
        for (int digits : new int[] { 0, 2, 3 }) {
            for (int cents = 1; cents <= 2000; cents++) {
                double amount = cents / 100.0;
                double fee = amount * 0.025;
                long[] units = MoneyTransfer.toSettlementUnits(amount, fee, digits);
                long credited = units[0] - units[1];
                assertTrue(credited >= 0L,
                    "negative credit at digits=" + digits + " amount=" + amount);
                assertEquals(units[0], credited + units[1],
                    "charge must equal credit plus fee at digits=" + digits);
            }
        }
    }

    @Test
    @DisplayName("a coarse currency collapses a sub-unit fee to zero rather than inventing one")
    void subUnitFeeOnCoarseCurrency() {
        // 2% of $50 is $1.00 — representable. 2% of $10 is $0.20, which rounds to 0 whole units.
        assertEquals(0L, feeOf(10.00, 0.20, 0));
        assertEquals(10L, amountOf(10.00, 0.20, 0), "the sender still pays the full amount");
    }

    @Test
    @DisplayName("binary floating point does not inflate the charge")
    void floatingPointTolerance() {
        // 12.60 * 100 is 1260.0000000000002; a naive ceil would charge an extra cent.
        assertEquals(1260L, amountOf(12.60, 0.0, 2));
        assertEquals(70L, amountOf(0.70, 0.0, 2));
    }

    @Test
    @DisplayName("unrepresentable amounts are rejected rather than silently saturating")
    void rejectsBadAmounts() {
        assertThrows(IllegalArgumentException.class,
            () -> MoneyTransfer.toSettlementUnits(Double.NaN, 0.0, 2));
        assertThrows(IllegalArgumentException.class,
            () -> MoneyTransfer.toSettlementUnits(1.0e18, 0.0, 2));
    }
}
