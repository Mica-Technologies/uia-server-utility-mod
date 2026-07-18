package com.micatechnologies.minecraft.sum.sleep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the pure sleep-vote math ({@link SleepVoteHandler#requiredSleepers}) and the
 * action-bar progress line ({@link SleepVoteHandler#progressLine}). Covers the
 * ceiling-division threshold rule documented in the config ("50% on a server of 3 needs 2")
 * and the never-below-1 floor.
 */
class SleepVoteMathTest {

    @Test
    void fiftyPercentOfThreeNeedsTwo() {
        // The documented example in the sleep_vote.thresholdPercent config description.
        assertEquals(2, SleepVoteHandler.requiredSleepers(3, 50));
    }

    @Test
    void ceilingDivisionRoundsUp() {
        assertEquals(1, SleepVoteHandler.requiredSleepers(1, 50));
        assertEquals(1, SleepVoteHandler.requiredSleepers(2, 50));
        assertEquals(3, SleepVoteHandler.requiredSleepers(5, 50));
        assertEquals(5, SleepVoteHandler.requiredSleepers(10, 50));
        assertEquals(7, SleepVoteHandler.requiredSleepers(10, 70));
        assertEquals(1, SleepVoteHandler.requiredSleepers(10, 1));
    }

    @Test
    void hundredPercentRequiresEveryone() {
        for (int total = 1; total <= 20; total++) {
            assertEquals(total, SleepVoteHandler.requiredSleepers(total, 100));
        }
    }

    @Test
    void requiredIsNeverBelowOne() {
        // Even a 1% threshold on a tiny server must not allow a zero-sleeper skip.
        for (int total = 1; total <= 10; total++) {
            for (int threshold = 1; threshold <= 100; threshold++) {
                int required = SleepVoteHandler.requiredSleepers(total, threshold);
                assertTrue(required >= 1,
                    "required < 1 for total=" + total + " threshold=" + threshold);
                assertTrue(required <= total,
                    "required > total for total=" + total + " threshold=" + threshold);
            }
        }
    }

    @Test
    void progressLineFormat() {
        assertEquals("3/5 sleeping (need 3 to skip)", SleepVoteHandler.progressLine(3, 5, 3));
        assertEquals("1/2 sleeping (need 1 to skip)", SleepVoteHandler.progressLine(1, 2, 1));
    }

    @Test
    void ticksUntilMorningIsZeroAtDawn() {
        // dayTime 0 = morning already, so no advance; a full day later is likewise 0.
        assertEquals(0, SleepVoteHandler.ticksUntilMorning(0));
        assertEquals(0, SleepVoteHandler.ticksUntilMorning(24000));
    }

    @Test
    void ticksUntilMorningCountsToTheNextDawn() {
        assertEquals(11000, SleepVoteHandler.ticksUntilMorning(13000)); // night
        assertEquals(1, SleepVoteHandler.ticksUntilMorning(23999));     // just before dawn
        assertEquals(18000, SleepVoteHandler.ticksUntilMorning(6000));  // midday
        // Multi-day world time wraps via % 24000.
        assertEquals(24000 - (1_000_000 % 24000), SleepVoteHandler.ticksUntilMorning(1_000_000));
    }
}
