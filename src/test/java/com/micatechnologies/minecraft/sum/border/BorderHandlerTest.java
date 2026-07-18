package com.micatechnologies.minecraft.sum.border;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the pure border geometry extracted from {@link BorderHandler}: inside-test, bounce clamp,
 * and loop wrap. The live handler wraps these around {@code player.setPositionAndUpdate} and chat
 * throttling, which need a server player; the math itself is pure.
 */
class BorderHandlerTest {

    private static final double EPS = 1e-9;

    @Test
    void isInsideIsInclusiveOnBothAxes() {
        assertTrue(BorderHandler.isInside(0, 0, 100));
        assertTrue(BorderHandler.isInside(100, 100, 100), "the edge counts as inside");
        assertTrue(BorderHandler.isInside(-100, -100, 100));
        assertFalse(BorderHandler.isInside(101, 0, 100));
        assertFalse(BorderHandler.isInside(0, 101, 100));
    }

    @Test
    void clampToInsetPullsBackToOneInsideTheEdge() {
        assertEquals(99.0, BorderHandler.clampToInset(150, 100), EPS);
        assertEquals(-99.0, BorderHandler.clampToInset(-150, 100), EPS);
        assertEquals(99.0, BorderHandler.clampToInset(100, 100), EPS, "on the edge → inset");
        assertEquals(50.0, BorderHandler.clampToInset(50, 100), EPS, "inside → unchanged");
    }

    @Test
    void loopWrapTeleportsAcrossOnlyWhenBeyondRadius() {
        assertEquals(-99.0, BorderHandler.loopWrap(150, 100), EPS);
        assertEquals(99.0, BorderHandler.loopWrap(-150, 100), EPS);
        assertEquals(50.0, BorderHandler.loopWrap(50, 100), EPS, "inside → unchanged");
        assertEquals(100.0, BorderHandler.loopWrap(100, 100), EPS, "exactly on radius is not > radius");
        assertEquals(-99.0, BorderHandler.loopWrap(101, 100), EPS);
    }
}
