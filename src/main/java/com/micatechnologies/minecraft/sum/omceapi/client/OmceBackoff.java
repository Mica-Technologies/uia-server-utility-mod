package com.micatechnologies.minecraft.sum.omceapi.client;

import java.util.Random;

/**
 * Exponential backoff with full jitter for retrying transient failures.
 *
 * <p>Full jitter (a uniform draw from {@code [0, cap]}) rather than a fixed delay, because every
 * Minecraft server talking to one economy service will otherwise retry in lockstep after an
 * outage and hammer it back down the moment it recovers.
 *
 * <p>Pure and deterministic given a seeded {@link Random}, so the schedule is unit-testable.
 */
public final class OmceBackoff {

    /** Delay before the first retry, before jitter. */
    public static final long BASE_DELAY_MS = 250L;

    /** Ceiling on the pre-jitter delay, so a long retry chain can't stall for minutes. */
    public static final long MAX_DELAY_MS = 8_000L;

    private final Random random;

    public OmceBackoff() {
        this(new Random());
    }

    public OmceBackoff(Random random) {
        this.random = random == null ? new Random() : random;
    }

    /**
     * Delay before the given retry.
     *
     * @param attempt 1 for the delay before the first retry, 2 before the second, and so on.
     * @param retryAfterMs a service-supplied hint (from {@code Retry-After} or
     *     {@code error.retryAfterMs}); treated as a floor, since the service knows better than we
     *     do when it will be ready.
     * @return milliseconds to wait.
     */
    public long delayFor(int attempt, long retryAfterMs) {
        long jittered = jitter(cappedDelay(attempt));
        return Math.max(jittered, Math.max(0L, retryAfterMs));
    }

    /** The pre-jitter exponential delay for an attempt, capped at {@link #MAX_DELAY_MS}. */
    public static long cappedDelay(int attempt) {
        if (attempt <= 1) {
            return BASE_DELAY_MS;
        }
        // Shift rather than Math.pow, and bail out early: attempt is bounded by maxRetries (<=10),
        // but guarding here keeps this correct if that ever changes.
        int steps = Math.min(attempt - 1, 32);
        long delay = BASE_DELAY_MS << steps;
        return (delay <= 0L || delay > MAX_DELAY_MS) ? MAX_DELAY_MS : delay;
    }

    private long jitter(long cap) {
        if (cap <= 0L) {
            return 0L;
        }
        // nextLong has no bounded form on Java 8; nextDouble is adequate for scheduling jitter.
        return (long) (random.nextDouble() * (double) cap);
    }
}
