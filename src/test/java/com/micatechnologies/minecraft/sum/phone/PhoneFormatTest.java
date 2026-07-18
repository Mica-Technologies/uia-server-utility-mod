package com.micatechnologies.minecraft.sum.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests the pure phone display helpers extracted into {@link PhoneFormat}: the in-game clock,
 * the calculator number formatter, the ARGB brighten, and note first-line ellipsis.
 */
class PhoneFormatTest {

    // --- formatClock (tick 0 = 06:00, wraps every 24000) ---

    @Test
    void clockMapsTicksToTimeOfDay() {
        assertEquals("06:00", PhoneFormat.formatClock(0));
        assertEquals("12:00", PhoneFormat.formatClock(6000));
        assertEquals("18:00", PhoneFormat.formatClock(12000));
        assertEquals("00:00", PhoneFormat.formatClock(18000));
    }

    @Test
    void clockWrapsNegativeAndLargeTicks() {
        assertEquals("05:00", PhoneFormat.formatClock(-1000));
        assertEquals("06:00", PhoneFormat.formatClock(24000)); // full day later
    }

    // --- formatCalc ---

    @Test
    void calcPrintsWholeNumbersWithoutDecimals() {
        assertEquals("5", PhoneFormat.formatCalc(5.0));
        assertEquals("0", PhoneFormat.formatCalc(0.0));
        assertEquals("-7", PhoneFormat.formatCalc(-7.0));
    }

    @Test
    void calcErrorsOnNonFinite() {
        assertEquals("Err", PhoneFormat.formatCalc(Double.NaN));
        assertEquals("Err", PhoneFormat.formatCalc(1.0 / 0.0));
        assertEquals("Err", PhoneFormat.formatCalc(Double.NEGATIVE_INFINITY));
    }

    @Test
    void calcTrimsTrailingZeros() {
        assertEquals("0.5", PhoneFormat.formatCalc(0.5));
        assertEquals("2.5", PhoneFormat.formatCalc(2.5));
        assertEquals("3.14", PhoneFormat.formatCalc(3.14));
    }

    // --- brighten ---

    @Test
    void brightenAddsPerChannelAndClamps() {
        assertEquals(0xFF141414, PhoneFormat.brighten(0xFF000000));
        assertEquals(0xFFFFFFFF, PhoneFormat.brighten(0xFFFFFFFF), "channels clamp at 255");
    }

    @Test
    void brightenPreservesAlpha() {
        assertEquals(0x80243444, PhoneFormat.brighten(0x80102030));
    }

    // --- firstLine ---

    @Test
    void firstLineTakesUpToNewline() {
        assertEquals("a", PhoneFormat.firstLine("a\nb"));
        assertEquals("", PhoneFormat.firstLine(""));
        assertEquals("short", PhoneFormat.firstLine("short"));
    }

    @Test
    void firstLineEllipsizesPastTwentyTwo() {
        assertEquals("abcdefghijklmnopqrstuv…",
            PhoneFormat.firstLine("abcdefghijklmnopqrstuvwxyz1234"));
        // Exactly 22 chars is left intact (the cut is > 22, not >= 22).
        String exactly22 = "abcdefghijklmnopqrstuv";
        assertEquals(exactly22, PhoneFormat.firstLine(exactly22));
    }
}
