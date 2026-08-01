package com.micatechnologies.minecraft.sum.omceapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Money conversion is the boundary between SUM's double dollars and the protocol's integer minor
 * units, so an error here silently mis-charges every player.
 */
class OmceMoneyTest {

    @Test
    @DisplayName("cents scale converts both directions")
    void centsRoundTrip() {
        assertEquals(12500L, OmceMoney.toMinorUnits(125.00d, 2));
        assertEquals(125.00d, OmceMoney.toDollars(12500L, 2), 1e-9);
        assertEquals(1L, OmceMoney.toMinorUnits(0.01d, 2));
        assertEquals(0L, OmceMoney.toMinorUnits(0.0d, 2));
    }

    @Test
    @DisplayName("whole-unit currency collapses cents")
    void wholeUnitScale() {
        assertEquals(12500L, OmceMoney.toMinorUnits(12500d, 0));
        assertEquals(13L, OmceMoney.toMinorUnits(12.6d, 0), "half-up rounds 12.6 to 13");
    }

    @Test
    @DisplayName("toMinorUnits rounds half-up")
    void halfUpRounding() {
        assertEquals(3L, OmceMoney.toMinorUnits(2.5d, 0));
        assertEquals(2L, OmceMoney.toMinorUnits(2.4d, 0));
        assertEquals(1251L, OmceMoney.toMinorUnits(12.505d, 2));
    }

    @Test
    @DisplayName("priceToMinorUnits rounds a charge UP so a coarse currency never under-settles")
    void pricesRoundUp() {
        // The value of the asymmetry: on a whole-unit currency a $12.01 price must charge 13,
        // not 12, or every sale leaks value.
        assertEquals(13L, OmceMoney.priceToMinorUnits(12.01d, 0));
        assertEquals(13L, OmceMoney.priceToMinorUnits(12.99d, 0));
        assertEquals(12L, OmceMoney.priceToMinorUnits(12.0d, 0), "an exact price must not inflate");
    }

    @Test
    @DisplayName("price rounding is not fooled by binary floating point")
    void priceRoundingTolerance() {
        // 12.60 * 100 is 1260.0000000000002 in IEEE 754; a naive ceil() would charge $12.61.
        assertEquals(1260L, OmceMoney.priceToMinorUnits(12.60d, 2));
        assertEquals(70L, OmceMoney.priceToMinorUnits(0.70d, 2));
        assertEquals(2900L, OmceMoney.priceToMinorUnits(29.00d, 2));
    }

    @Test
    @DisplayName("non-finite and overflowing amounts are rejected, not silently saturated")
    void rejectsBadAmounts() {
        assertThrows(IllegalArgumentException.class, () -> OmceMoney.toMinorUnits(Double.NaN, 2));
        assertThrows(IllegalArgumentException.class,
            () -> OmceMoney.toMinorUnits(Double.POSITIVE_INFINITY, 2));
        // Without the explicit guard this would saturate to Long.MAX_VALUE and look plausible.
        assertThrows(IllegalArgumentException.class, () -> OmceMoney.toMinorUnits(1.0e18d, 2));
    }

    @Test
    @DisplayName("scale factor and digit clamping")
    void scaleFactors() {
        assertEquals(1L, OmceMoney.scaleFactor(0));
        assertEquals(100L, OmceMoney.scaleFactor(2));
        assertEquals(1000L, OmceMoney.scaleFactor(3));
        assertEquals(0, OmceMoney.clampDigits(-5));
        assertEquals(OmceMoney.MAX_MINOR_UNIT_DIGITS, OmceMoney.clampDigits(99));
    }

    @Test
    @DisplayName("representability check catches precision a currency cannot settle")
    void representability() {
        assertTrue(OmceMoney.isExactlyRepresentable(12.50d, 2));
        assertFalse(OmceMoney.isExactlyRepresentable(12.50d, 0));
        assertTrue(OmceMoney.isExactlyRepresentable(12.0d, 0));
    }

    @Test
    @DisplayName("formatting honours scale and symbol")
    void formatting() {
        assertEquals("$125.00", OmceMoney.format(12500L, 2, "$"));
        assertEquals("$12,500", OmceMoney.format(12500L, 0, "$"));
        assertEquals("125.00", OmceMoney.format(12500L, 2, null));
    }
}
