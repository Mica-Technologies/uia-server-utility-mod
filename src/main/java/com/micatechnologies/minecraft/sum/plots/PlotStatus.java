package com.micatechnologies.minecraft.sum.plots;

/**
 * Lifecycle states for a {@link SumPlot}. The status changes via {@code /sum plots} commands —
 * an admin creates a plot in {@link #FOR_SALE}, a player {@code /sum plots buy}s into
 * {@link #OWNED}, owner can re-list with {@code /sum plots sell} which goes back to
 * {@link #FOR_SALE} (with a new price).
 */
public enum PlotStatus {
    /** No owner. Anyone can buy at the listed price. */
    FOR_SALE,
    /** Has an owner. Listed price is informational only (last sale price). */
    OWNED,
    /** Reserved by an admin (not yet for sale). Same as FOR_SALE but doesn't appear in browser. */
    RESERVED,
    /** Inactivity-expired or rental-expired. Renderable but not buyable until re-listed. */
    EXPIRED;

    public static PlotStatus fromName(String name, PlotStatus fallback) {
        if (name == null) return fallback;
        for (PlotStatus s : values()) {
            if (s.name().equalsIgnoreCase(name)) return s;
        }
        return fallback;
    }
}
