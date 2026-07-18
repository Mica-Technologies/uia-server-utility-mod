package com.micatechnologies.minecraft.sum.huds.presets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.micatechnologies.minecraft.sum.huds.presets.HudLayout.Column;
import com.micatechnologies.minecraft.sum.huds.presets.HudLayout.Direction;
import org.junit.jupiter.api.Test;

/**
 * Tests the pure immutable {@link HudLayout} builder: {@code place} (single-row column),
 * {@code column} (multi-row stack), insertion order, and the unmodifiable built list. Note:
 * {@code HudPresets} itself can't be unit-tested — its static catalog constructs OneConfig
 * {@code OneColor}s, and OneConfig is a compileOnly dependency absent at test runtime.
 */
class HudLayoutTest {

    @Test
    void placeAddsASingleRowDownColumn() {
        HudLayout layout = HudLayout.builder("Test").place("coordsHud", 960f, 10f).build();
        assertEquals(1, layout.columns.size());
        Column c = layout.columns.get(0);
        assertEquals(960f, c.x);
        assertEquals(10f, c.anchorY);
        assertSame(Direction.DOWN, c.direction);
        assertEquals(1, c.hudNames.size());
        assertEquals("coordsHud", c.hudNames.get(0));
    }

    @Test
    void columnKeepsNameOrderAnchorAndDirection() {
        HudLayout layout = HudLayout.builder("Test")
            .column(0f, 1077f, Direction.UP, "a", "b", "c").build();
        Column c = layout.columns.get(0);
        assertEquals(0f, c.x);
        assertEquals(1077f, c.anchorY);
        assertSame(Direction.UP, c.direction);
        assertEquals(java.util.Arrays.asList("a", "b", "c"), c.hudNames);
    }

    @Test
    void columnsRetainInsertionOrder() {
        HudLayout layout = HudLayout.builder("Test")
            .place("solo", 5f, 5f)
            .column(0f, 100f, Direction.UP, "x", "y")
            .build();
        assertEquals(2, layout.columns.size());
        assertEquals("solo", layout.columns.get(0).hudNames.get(0));
        assertEquals("x", layout.columns.get(1).hudNames.get(0));
    }

    @Test
    void builtColumnListIsUnmodifiable() {
        HudLayout layout = HudLayout.builder("Test").place("a", 0f, 0f).build();
        assertThrows(UnsupportedOperationException.class,
            () -> layout.columns.add(new Column(0f, 0f, Direction.DOWN, java.util.Collections.emptyList())));
    }

    @Test
    void columnWithNoNamesIsEmptyNotNull() {
        HudLayout layout = HudLayout.builder("Test").column(0f, 0f, Direction.DOWN).build();
        assertTrue(layout.columns.get(0).hudNames.isEmpty());
    }
}
