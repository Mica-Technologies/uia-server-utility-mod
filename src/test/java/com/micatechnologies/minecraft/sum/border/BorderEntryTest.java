package com.micatechnologies.minecraft.sum.border;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/** Trivial value-object checks for {@link BorderEntry} (TESTING_PLAN.md §4.8 M6 config model). */
class BorderEntryTest {

    @Test
    void retainsConstructorValues() {
        BorderEntry e = new BorderEntry(-1, 1500.0, BorderEntry.Mode.LOOP);
        assertEquals(-1, e.getDimId());
        assertEquals(1500.0, e.getRadius());
        assertSame(BorderEntry.Mode.LOOP, e.getMode());
    }

    @Test
    void hasExactlyBounceAndLoopModes() {
        assertEquals(2, BorderEntry.Mode.values().length);
        assertSame(BorderEntry.Mode.BOUNCE, BorderEntry.Mode.valueOf("BOUNCE"));
        assertSame(BorderEntry.Mode.LOOP, BorderEntry.Mode.valueOf("LOOP"));
    }
}
