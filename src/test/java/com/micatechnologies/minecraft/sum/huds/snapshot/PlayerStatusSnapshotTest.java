package com.micatechnologies.minecraft.sum.huds.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests the {@link PlayerStatusSnapshot} value object: the null-coalescing constructor (so the
 * HUDs never render "null" for a plot name/owner) and the {@code empty()} "no data yet" sentinel.
 */
class PlayerStatusSnapshotTest {

    @Test
    void constructorCoalescesNullNames() {
        PlayerStatusSnapshot s = new PlayerStatusSnapshot(0L, 250.75, "", null, null, 0, 0, -1);
        assertEquals("", s.plotName);
        assertEquals("", s.plotOwner);
    }

    @Test
    void constructorPreservesNonNullValues() {
        PlayerStatusSnapshot s = new PlayerStatusSnapshot(1234L, 250.75, "", "Downtown", "alex", 3, 14400, 36000);
        assertEquals(1234L, s.vaultBillValue);
        assertEquals("Downtown", s.plotName);
        assertEquals("alex", s.plotOwner);
        assertEquals(3, s.jobsAvailable);
        assertEquals(14400, s.loyaltyTicks);
        assertEquals(36000, s.nextMilestoneTicks);
    }

    @Test
    void emptyIsTheZeroSentinel() {
        PlayerStatusSnapshot s = PlayerStatusSnapshot.empty();
        assertEquals(0L, s.vaultBillValue);
        assertEquals("", s.plotName);
        assertEquals("", s.plotOwner);
        assertEquals(0, s.jobsAvailable);
        assertEquals(0, s.loyaltyTicks);
        assertEquals(-1, s.nextMilestoneTicks, "empty uses -1 for 'no next milestone'");
    }

    @Test
    void bankBalanceHoldsFullLongRange() {
        assertEquals(Long.MAX_VALUE,
            new PlayerStatusSnapshot(Long.MAX_VALUE, 0.0, "", "", "", 0, 0, -1).vaultBillValue);
    }
}
