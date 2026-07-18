package com.micatechnologies.minecraft.sum.huds;

/**
 * Pure text/number formatting helpers extracted from the individual HUD {@code getText} methods.
 * The HUD classes themselves extend OneConfig's {@code Hud} (a {@code compileOnly} dependency
 * absent at test runtime), so the formatting math is homed here — in a class with no OneConfig
 * or Minecraft dependencies — where it can be unit-tested. Each HUD computes its live inputs
 * (from {@code Minecraft.getMinecraft()} etc.) and delegates the final string formatting here.
 */
public final class HudFormat {

    private HudFormat() {}

    /** Compass abbreviations, index 0..7 = yaw 0 (south) increasing clockwise per 45° octant. */
    private static final String[] DIR_SHORT = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final String[] DIR_LONG = {
        "South", "South-West", "West", "North-West",
        "North", "North-East", "East", "South-East"
    };

    /**
     * Maps a Minecraft yaw to one of eight compass octants. Vanilla yaw 0 faces south and
     * increases clockwise; each octant is centered on its cardinal, so the +22.5° offset shifts
     * the bucket edges to the half-points and truncation (not rounding) selects the bucket —
     * facing due south returns "S", not the neighbouring octant.
     */
    public static String direction(float yaw, boolean longName) {
        yaw %= 360f;
        if (yaw < 0f) yaw += 360f;
        int octant = (int) ((yaw + 22.5f) / 45f) % 8;
        return longName ? DIR_LONG[octant] : DIR_SHORT[octant];
    }

    /**
     * Formats a Minecraft world time (ticks) as a wall clock. dayTime 0 = 06:00; the value wraps
     * every 24000 ticks. {@code twentyFourHour} chooses {@code HH:mm} vs {@code h:mm AM/PM}.
     */
    public static String inGameTime(long worldTime, boolean twentyFourHour) {
        long ticks = worldTime % 24000L;
        int minutes = (int) ((ticks * 1440L) / 24000L) + 360; // +6h offset
        minutes %= 1440;
        int h24 = minutes / 60;
        int m = minutes % 60;
        if (twentyFourHour) {
            return String.format("%02d:%02d", h24, m);
        }
        int h12 = h24 % 12;
        if (h12 == 0) h12 = 12;
        return String.format("%d:%02d %s", h12, m, h24 < 12 ? "AM" : "PM");
    }

    /** JVM heap readout: a rounded percentage, or "used / max GB" to one decimal place. */
    public static String memory(long used, long max, boolean asPercent) {
        if (asPercent) {
            return Math.round(100.0 * used / max) + "%";
        }
        return String.format("%.1f / %.1f GB", used / 1073741824.0, max / 1073741824.0);
    }

    /**
     * Server TPS from the real-millis between two 20-tick time packets, clamped to [0, 20] so a
     * hiccup can't display an impossible rate. Formatted to two decimals.
     */
    public static String tps(long deltaMs) {
        double tps = Math.min(20.0, Math.max(0.0, 20000.0 / deltaMs));
        return String.format("%.2f", tps);
    }

    /** Held-item durability: remaining points, optionally with a rounded percentage. */
    public static String durability(int remaining, int max, boolean showPercent) {
        if (!showPercent) {
            return Integer.toString(remaining);
        }
        int pct = max == 0 ? 0 : Math.round(remaining * 100f / max);
        return remaining + " (" + pct + "%)";
    }

    /** Frame time in milliseconds from an FPS reading; "?ms" when FPS is unavailable. */
    public static String frameTime(int fps) {
        if (fps <= 0) {
            return "?ms";
        }
        return Math.round(1000f / fps) + "ms";
    }

    /** Elapsed session time as {@code HH:mm:ss} (hours are not capped at 24). */
    public static String playtime(long elapsedSec) {
        long h = elapsedSec / 3600L;
        long m = (elapsedSec % 3600L) / 60L;
        long s = elapsedSec % 60L;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    /** In-game day number (1-based) from world time. */
    public static long dayNumber(long worldTime) {
        return worldTime / 24000L + 1L;
    }

    /**
     * Distance to the nearest edge of a square, origin-centered border of the given radius:
     * {@code radius - max(|x|, |z|)}, rounded. Positive inside, negative outside.
     */
    public static long borderDistance(double radius, double x, double z) {
        return Math.round(radius - Math.max(Math.abs(x), Math.abs(z)));
    }

    /** Loyalty progress: accrued minutes, plus the next milestone's minutes when there is one. */
    public static String loyalty(int loyaltyTicks, int nextMilestoneTicks) {
        int curMin = loyaltyTicks / (20 * 60);
        if (nextMilestoneTicks < 0) {
            return curMin + "m";
        }
        int nextMin = nextMilestoneTicks / (20 * 60);
        return curMin + "m / " + nextMin + "m";
    }

    /** Experience readout: level, optionally with the bar progress as a percentage. */
    public static String xp(int level, float progress, boolean showProgress) {
        if (!showProgress) {
            return "L" + level;
        }
        int pct = Math.round(progress * 100f);
        return "L" + level + " (" + pct + "%)";
    }

    /** Plot label: name, optionally suffixed with " (owner)". Assumes a non-empty plot name. */
    public static String plotInfo(String plotName, String plotOwner, boolean showOwner) {
        if (!showOwner || plotOwner.isEmpty()) {
            return plotName;
        }
        return plotName + " (" + plotOwner + ")";
    }

    /** Blocks of head-room between the player and the world's build height. */
    public static int roomAbove(int worldHeight, double posY) {
        return worldHeight - (int) Math.floor(posY);
    }
}
