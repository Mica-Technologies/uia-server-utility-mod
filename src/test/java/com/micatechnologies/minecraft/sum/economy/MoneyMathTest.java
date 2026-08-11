package com.micatechnologies.minecraft.sum.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The single currency-rounding rule shared by {@code /pay} and the public economy API.
 *
 * <p>It is shared precisely so the same amount cannot become different money depending on which
 * code path a player used, which makes its edge cases worth pinning: the half-cent boundary, and
 * the float noise that made rounding necessary in the first place.
 */
class MoneyMathTest {

    @Test
    @DisplayName("whole cents are unchanged")
    void wholeCentsUnchanged() {
        assertEquals(10.0, MoneyMath.roundToCents(10.0), 0.0);
        assertEquals(19.99, MoneyMath.roundToCents(19.99), 1.0e-9);
        assertEquals(0.01, MoneyMath.roundToCents(0.01), 1.0e-9);
    }

    @Test
    @DisplayName("half a cent rounds up, the way a till does")
    void halfCentRoundsUp() {
        assertEquals(10.01, MoneyMath.roundToCents(10.005), 1.0e-9);
        assertEquals(0.01, MoneyMath.roundToCents(0.005), 1.0e-9);
    }

    @Test
    @DisplayName("below half a cent rounds down")
    void belowHalfCentRoundsDown() {
        assertEquals(10.0, MoneyMath.roundToCents(10.004), 1.0e-9);
        assertEquals(0.0, MoneyMath.roundToCents(0.004), 1.0e-9);
    }

    @Test
    @DisplayName("float noise is absorbed rather than propagated")
    void absorbsFloatNoise() {
        // The reason this class exists: 0.1 + 0.2 is 0.30000000000000004 as a double, and a
        // balance that reads $19.999999999999996 refuses a purchase the player can plainly afford.
        assertEquals(0.3, MoneyMath.roundToCents(0.1 + 0.2), 1.0e-9);
        assertEquals(20.0, MoneyMath.roundToCents(19.999999999999996), 1.0e-9);
    }

    @Test
    @DisplayName("zero and negatives round without surprises")
    void zeroAndNegatives() {
        assertEquals(0.0, MoneyMath.roundToCents(0.0), 0.0);
        assertEquals(-5.0, MoneyMath.roundToCents(-5.0), 1.0e-9);
        assertEquals(-5.0, MoneyMath.roundToCents(-4.999), 1.0e-9);
    }

    @Test
    @DisplayName("non-finite input passes through instead of becoming a number")
    void nonFinitePassesThrough() {
        // Turning NaN into 0.0 here would disguise a caller's bug as a free transaction; the
        // guard in front of every spend rejects it explicitly instead.
        assertTrue(Double.isNaN(MoneyMath.roundToCents(Double.NaN)));
        assertEquals(Double.POSITIVE_INFINITY, MoneyMath.roundToCents(Double.POSITIVE_INFINITY));
        assertEquals(Double.NEGATIVE_INFINITY, MoneyMath.roundToCents(Double.NEGATIVE_INFINITY));
    }

    @Test
    @DisplayName("MoneyTransfer rounds through the same rule, so /pay and the API agree")
    void moneyTransferDelegates() {
        assertEquals(MoneyMath.roundToCents(10.005), MoneyTransfer.roundToCents(10.005), 0.0);
        assertEquals(MoneyMath.roundToCents(0.1 + 0.2), MoneyTransfer.roundToCents(0.1 + 0.2), 0.0);
    }
}
