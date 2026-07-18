package com.micatechnologies.minecraft.sum.loyalty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * Tests the {@link LoyaltyMilestone} value object: the {@code minutes → ticks} conversion
 * (20 ticks/sec × 60) and getter fidelity.
 */
class LoyaltyMilestoneTest {

    @Test
    void ticksAreMinutesTimesTwelveHundred() {
        assertEquals(1200, new LoyaltyMilestone(1, LoyaltyMilestone.Type.MONEY, "x").getTicks());
        assertEquals(0, new LoyaltyMilestone(0, LoyaltyMilestone.Type.MONEY, "x").getTicks());
        assertEquals(72000, new LoyaltyMilestone(60, LoyaltyMilestone.Type.MONEY, "x").getTicks());
    }

    @Test
    void gettersReturnConstructorValues() {
        LoyaltyMilestone m = new LoyaltyMilestone(120, LoyaltyMilestone.Type.COMMAND, "say hi");
        assertEquals(120, m.getMinutes());
        assertSame(LoyaltyMilestone.Type.COMMAND, m.getType());
        assertEquals("say hi", m.getValue());
    }
}
