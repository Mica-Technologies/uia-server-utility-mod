package com.micatechnologies.minecraft.sum.plots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/** {@link PlotStatus#fromName} parsing — case-insensitivity and fallback behavior. */
class PlotStatusTest {

    @Test
    void parsesExactNames() {
        for (PlotStatus s : PlotStatus.values()) {
            assertSame(s, PlotStatus.fromName(s.name(), PlotStatus.OWNED));
        }
    }

    @Test
    void parsingIsCaseInsensitive() {
        assertSame(PlotStatus.FOR_SALE, PlotStatus.fromName("for_sale", PlotStatus.OWNED));
        assertSame(PlotStatus.FOR_SALE, PlotStatus.fromName("For_Sale", PlotStatus.OWNED));
        assertSame(PlotStatus.RESERVED, PlotStatus.fromName("reserved", PlotStatus.OWNED));
    }

    @Test
    void unknownNameYieldsFallback() {
        assertSame(PlotStatus.RESERVED, PlotStatus.fromName("bogus", PlotStatus.RESERVED));
        assertSame(PlotStatus.EXPIRED, PlotStatus.fromName("", PlotStatus.EXPIRED));
    }

    @Test
    void nullNameYieldsFallback() {
        assertSame(PlotStatus.OWNED, PlotStatus.fromName(null, PlotStatus.OWNED));
    }

    @Test
    void hasExactlyTheFourExpectedStates() {
        // A new status would change buyability/browser-visibility logic elsewhere — flag it here.
        assertEquals(4, PlotStatus.values().length);
    }
}
