package com.micatechnologies.minecraft.sum.huds;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests the placeholder-substitution scanner via {@link TextPlaceholders#applyExample}, the
 * pure preview path that shares the exact token-scanning loop with the live {@code apply()}.
 * Directly covers TESTING_PLAN.md §4.6 Task 15 — including the load-bearing
 * "unknown token left intact so typos are visible" rule.
 *
 * <p>The live {@code apply()} can't be unit-tested (it reads {@code Minecraft.getMinecraft()}),
 * but the scanning/splicing logic that decides what to substitute is identical and fully
 * exercised here.
 */
class TextPlaceholdersTest {

    @Test
    void nullBecomesEmptyString() {
        assertEquals("", TextPlaceholders.applyExample(null));
    }

    @Test
    void emptyStringStaysEmpty() {
        assertEquals("", TextPlaceholders.applyExample(""));
    }

    @Test
    void stringWithoutTokensIsUnchanged() {
        assertEquals("just some text", TextPlaceholders.applyExample("just some text"));
    }

    @Test
    void substitutesCoordinateTokens() {
        assertEquals("Steve @ 100, 64, -250",
            TextPlaceholders.applyExample("%player% @ %x%, %y%, %z%"));
    }

    @Test
    void substitutesContextTokens() {
        assertEquals("Plains / overworld / 12:00",
            TextPlaceholders.applyExample("%biome% / %dim% / %time%"));
    }

    @Test
    void unknownTokenIsLeftIntact() {
        // The whole point: a typo'd %bogus% stays visible rather than vanishing.
        assertEquals("%bogus% 100", TextPlaceholders.applyExample("%bogus% %x%"));
    }

    @Test
    void tokenMatchingIsCaseInsensitive() {
        assertEquals("Steve", TextPlaceholders.applyExample("%PLAYER%"));
        assertEquals("Steve", TextPlaceholders.applyExample("%Player%"));
    }

    @Test
    void trailingUnmatchedPercentIsEmittedLiterally() {
        assertEquals("50%", TextPlaceholders.applyExample("50%"));
        assertEquals("100 is 50%", TextPlaceholders.applyExample("%x% is 50%"));
    }

    @Test
    void emptyTokenIsLeftIntact() {
        // "%%" has an empty token name, which is unknown → left as-is.
        assertEquals("%%", TextPlaceholders.applyExample("%%"));
    }

    @Test
    void dateAndDirectionTokensResolve() {
        assertEquals("2026-05-20", TextPlaceholders.applyExample("%date%"));
        assertEquals("facing N", TextPlaceholders.applyExample("facing %direction%"));
    }

    @Test
    void adjacentTokensConcatenate() {
        assertEquals("10064", TextPlaceholders.applyExample("%x%%y%"));
    }
}
