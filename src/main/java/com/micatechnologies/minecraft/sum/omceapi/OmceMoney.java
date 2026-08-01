package com.micatechnologies.minecraft.sum.omceapi;

import java.util.Locale;

/**
 * Conversion between SUM's internal money representation (a {@code double} count of whole dollars)
 * and the protocol's (a {@code long} count of minor units).
 *
 * <p>The protocol forbids floating-point amounts on the wire, so this is the only place the two
 * representations meet. The scale comes from the service's declared {@code minorUnitDigits}:
 *
 * <pre>
 *   digits = 2  ->  1 dollar = 100 minor units   (cents; the usual case)
 *   digits = 0  ->  1 dollar = 1 minor unit      (a whole-unit currency)
 *   digits = 3  ->  1 dollar = 1000 minor units
 * </pre>
 *
 * <p><b>Rounding direction is deliberately asymmetric.</b> {@link #toMinorUnits} rounds half-up,
 * which is right for reporting an amount that already exists. {@link #priceToMinorUnits} rounds
 * <i>up</i>, and is what callers must use when charging a player: on a coarse currency, rounding a
 * $12.60 price down to $12 would let the service settle less than the shop asked for, quietly
 * leaking value on every sale. Rounding up means the player is never charged less than the posted
 * price — and because SUM redisplays the rounded figure, never a price they were not quoted.
 */
public final class OmceMoney {

    /** Largest {@code minorUnitDigits} accepted. Beyond this, a long overflows on sane balances. */
    public static final int MAX_MINOR_UNIT_DIGITS = 6;

    /** Default scale when a service has not been contacted yet: cents. */
    public static final int DEFAULT_MINOR_UNIT_DIGITS = 2;

    private OmceMoney() {}

    /** @return 10^digits as a long. */
    public static long scaleFactor(int digits) {
        int d = clampDigits(digits);
        long factor = 1L;
        for (int i = 0; i < d; i++) {
            factor *= 10L;
        }
        return factor;
    }

    /** Clamps a service-declared digit count into the supported range. */
    public static int clampDigits(int digits) {
        if (digits < 0) {
            return 0;
        }
        return Math.min(digits, MAX_MINOR_UNIT_DIGITS);
    }

    /**
     * Converts dollars to minor units, rounding half-up. Use for reporting an existing amount.
     *
     * @throws IllegalArgumentException if {@code dollars} is not finite or would overflow a long.
     */
    public static long toMinorUnits(double dollars, int digits) {
        return convert(dollars, digits, false);
    }

    /**
     * Converts a <i>price</i> to minor units, rounding up to the next representable unit. Use
     * whenever a player is about to be charged, so a coarse currency can never settle for less
     * than the asking price.
     *
     * @throws IllegalArgumentException if {@code dollars} is not finite or would overflow a long.
     */
    public static long priceToMinorUnits(double dollars, int digits) {
        return convert(dollars, digits, true);
    }

    private static long convert(double dollars, int digits, boolean roundUp) {
        if (Double.isNaN(dollars) || Double.isInfinite(dollars)) {
            throw new IllegalArgumentException("Amount is not a finite number: " + dollars);
        }
        double scaled = dollars * (double) scaleFactor(digits);
        // Guard before the cast: (long) of an out-of-range double silently saturates to
        // Long.MAX_VALUE, which would turn a nonsense amount into a plausible-looking one.
        if (scaled > 9.0e18d || scaled < -9.0e18d) {
            throw new IllegalArgumentException("Amount overflows the protocol's 64-bit range: " + dollars);
        }
        double rounded = roundUp ? Math.ceil(scaled - epsilonFor(scaled)) : Math.floor(scaled + 0.5d);
        return (long) rounded;
    }

    /**
     * Tolerance used when rounding a price up, so a value that is only above the boundary through
     * binary-floating-point error does not push to the next unit. {@code 12.60 * 100} is
     * {@code 1260.0000000000002} in IEEE 754; without this, that charges $12.61.
     *
     * <p>Scaled relative to the magnitude so it stays meaningful for large amounts.
     */
    private static double epsilonFor(double scaled) {
        return Math.max(1.0e-9d, Math.abs(scaled) * 1.0e-12d);
    }

    /** Converts minor units back to a dollar value for SUM's internal use and display. */
    public static double toDollars(long minorUnits, int digits) {
        return (double) minorUnits / (double) scaleFactor(digits);
    }

    /**
     * Formats a minor-unit amount for display, using the service's scale and symbol.
     *
     * @param symbol currency symbol, e.g. {@code "$"}; null or empty renders without one.
     */
    public static String format(long minorUnits, int digits, String symbol) {
        int d = clampDigits(digits);
        String prefix = (symbol == null) ? "" : symbol;
        return prefix + String.format(Locale.ROOT, "%,." + d + "f", toDollars(minorUnits, d));
    }

    /**
     * True when a dollar value can be represented exactly at the given scale. SUM uses this to
     * warn an operator once at startup if their configured prices carry more precision than the
     * service's currency can settle.
     */
    public static boolean isExactlyRepresentable(double dollars, int digits) {
        if (Double.isNaN(dollars) || Double.isInfinite(dollars)) {
            return false;
        }
        double scaled = dollars * (double) scaleFactor(digits);
        return Math.abs(scaled - Math.floor(scaled + 0.5d)) < epsilonFor(scaled);
    }
}
