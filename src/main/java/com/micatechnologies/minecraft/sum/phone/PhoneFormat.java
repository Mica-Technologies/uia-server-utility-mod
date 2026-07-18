package com.micatechnologies.minecraft.sum.phone;

import java.util.Locale;

/**
 * Pure display-formatting helpers for the phone GUI, factored out of {@link GuiSumPhone} so they
 * can be unit-tested without loading the client {@code GuiScreen} class hierarchy. All methods
 * are side-effect-free string/number math.
 */
final class PhoneFormat {

    private PhoneFormat() {}

    /**
     * Formats an in-game world time (ticks) as a {@code HH:mm} 24-hour clock. Tick 0 is 06:00
     * (Minecraft's dawn), and the value wraps every 24000 ticks; negative inputs wrap cleanly.
     */
    static String formatClock(long worldTime) {
        long todTicks = ((worldTime % 24000L) + 24000L) % 24000L;
        long minutesOfDay = ((todTicks + 6000L) % 24000L) * 60L / 1000L;
        long hours = minutesOfDay / 60L;
        long minutes = minutesOfDay % 60L;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    /**
     * Formats a calculator result: whole values print as integers, {@code NaN}/{@code Infinity}
     * become {@code "Err"}, and other values print with trailing zeros trimmed and capped at 12
     * characters.
     */
    static String formatCalc(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "Err";
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e12) {
            return Long.toString((long) v);
        }
        String s = String.format(Locale.ROOT, "%.10g", v);
        if (s.contains(".") && !s.contains("e") && !s.contains("E")) {
            int end = s.length();
            while (end > 0 && s.charAt(end - 1) == '0') end--;
            if (end > 0 && s.charAt(end - 1) == '.') end--;
            s = s.substring(0, end);
        }
        if (s.length() > 12) s = s.substring(0, 12);
        return s;
    }

    /** Brightens a packed ARGB color by +20 per RGB channel (clamped at 255); alpha unchanged. */
    static int brighten(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 20);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + 20);
        int b = Math.min(255, (argb & 0xFF) + 20);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** First line of {@code s} (up to a newline), ellipsized past 22 characters. */
    static String firstLine(String s) {
        int nl = s.indexOf('\n');
        String line = nl < 0 ? s : s.substring(0, nl);
        if (line.length() > 22) line = line.substring(0, 22) + "…";
        return line;
    }
}
