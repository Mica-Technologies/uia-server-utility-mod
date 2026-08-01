package com.micatechnologies.minecraft.sum.omceapi.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Backoff shapes how a fleet of Minecraft servers behaves when a shared economy service comes
 * back after an outage, so the jitter is load-bearing rather than cosmetic.
 */
class OmceBackoffTest {

    @Test
    @DisplayName("delay doubles per attempt up to the cap")
    void exponentialGrowth() {
        assertEquals(250L, OmceBackoff.cappedDelay(1));
        assertEquals(500L, OmceBackoff.cappedDelay(2));
        assertEquals(1_000L, OmceBackoff.cappedDelay(3));
        assertEquals(2_000L, OmceBackoff.cappedDelay(4));
        assertEquals(OmceBackoff.MAX_DELAY_MS, OmceBackoff.cappedDelay(20), "must saturate, not overflow");
        assertEquals(250L, OmceBackoff.cappedDelay(0), "a non-positive attempt is treated as the first");
    }

    @Test
    @DisplayName("jitter keeps every delay within [0, cap]")
    void jitterStaysInRange() {
        OmceBackoff backoff = new OmceBackoff(new Random(1234L));
        for (int attempt = 1; attempt <= 6; attempt++) {
            long cap = OmceBackoff.cappedDelay(attempt);
            for (int i = 0; i < 200; i++) {
                long delay = backoff.delayFor(attempt, 0L);
                assertTrue(delay >= 0L && delay <= cap,
                    "attempt " + attempt + " produced " + delay + " outside [0," + cap + "]");
            }
        }
    }

    @Test
    @DisplayName("full jitter actually spreads retries out")
    void jitterIsNotConstant() {
        // Without this, every server retrying after an outage would fire simultaneously.
        OmceBackoff backoff = new OmceBackoff(new Random(99L));
        long first = backoff.delayFor(4, 0L);
        boolean sawDifferent = false;
        for (int i = 0; i < 50 && !sawDifferent; i++) {
            sawDifferent = backoff.delayFor(4, 0L) != first;
        }
        assertTrue(sawDifferent, "delays should vary between attempts");
    }

    @Test
    @DisplayName("a service Retry-After hint acts as a floor")
    void retryAfterIsAFloor() {
        OmceBackoff backoff = new OmceBackoff(new Random(7L));
        for (int i = 0; i < 100; i++) {
            assertTrue(backoff.delayFor(1, 5_000L) >= 5_000L,
                "the service knows better than we do when it will be ready");
        }
    }

    @Test
    @DisplayName("a negative hint is ignored rather than shortening the delay")
    void negativeHintIgnored() {
        OmceBackoff backoff = new OmceBackoff(new Random(3L));
        assertTrue(backoff.delayFor(2, -9_000L) >= 0L);
    }

    @Test
    @DisplayName("the schedule is reproducible for a seeded random")
    void deterministicWithSeed() {
        assertEquals(new OmceBackoff(new Random(42L)).delayFor(3, 0L),
            new OmceBackoff(new Random(42L)).delayFor(3, 0L));
    }
}
