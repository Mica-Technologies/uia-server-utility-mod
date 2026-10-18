package com.micatechnologies.minecraft.sum.roamer;

import net.minecraft.world.World;

/**
 * A server-wide cap on how many cells the emergency cube searches (fire exit, storm shelter)
 * may examine in one tick, shared by every roamer.
 *
 * <p>Each search already yields after {@code CELLS_PER_TICK} cells, but that bound was per
 * roamer: an alarm heard by 100 roamers started 100 searches at once, about 100,000 cells and
 * several hundred thousand block lookups in a single tick, every tick until they finished. With
 * this budget the same alarm costs at most {@link #CELLS_PER_TICK} cells a tick, and the
 * searches take turns: whichever roamers tick first in a tick spend it, the rest wait a tick.
 * Every search still finishes, it just finishes later when many run together.
 *
 * <p>Keyed by world and total world time, so it needs no tick event and resets on its own.
 * Server thread only, like the AI that calls it.
 */
public final class RoamerSearchBudget {

    /** Cells all searches together may examine per tick. Four full per-roamer slices. */
    static final int CELLS_PER_TICK = 4000;

    private static World budgetWorld;
    private static long budgetTick = Long.MIN_VALUE;
    private static int remaining;

    private RoamerSearchBudget() {}

    /**
     * Takes up to {@code wanted} cells from this tick's budget.
     *
     * @return how many cells the caller may examine now; 0 means wait for the next tick
     */
    static int take(World world, int wanted) {
        long now = world.getTotalWorldTime();
        if (world != budgetWorld || now != budgetTick) {
            budgetWorld = world;
            budgetTick = now;
            remaining = CELLS_PER_TICK;
        }
        int granted = Math.min(wanted, remaining);
        remaining -= granted;
        return granted;
    }

    /** Drops the reference to the last world. Called when the server stops. */
    public static void clear() {
        budgetWorld = null;
        budgetTick = Long.MIN_VALUE;
    }
}
