package com.micatechnologies.minecraft.sum.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link JobStatus#fromOrdinal} — the persisted-byte → enum mapping with an OPEN fallback
 * for any out-of-range value (so a corrupt or future status byte degrades safely).
 */
class JobStatusTest {

    @Test
    void validOrdinalsMapToConstants() {
        assertSame(JobStatus.OPEN, JobStatus.fromOrdinal(0));
        assertSame(JobStatus.CLAIMED, JobStatus.fromOrdinal(1));
        assertSame(JobStatus.SUBMITTED, JobStatus.fromOrdinal(2));
    }

    @Test
    void outOfRangeFallsBackToOpen() {
        assertSame(JobStatus.OPEN, JobStatus.fromOrdinal(3));
        assertSame(JobStatus.OPEN, JobStatus.fromOrdinal(-1));
        assertSame(JobStatus.OPEN, JobStatus.fromOrdinal(99));
    }

    @Test
    void hasExactlyThreeStates() {
        // A new state would need persistence + GUI handling; make adding one a conscious change.
        assertEquals(3, JobStatus.values().length);
    }
}
