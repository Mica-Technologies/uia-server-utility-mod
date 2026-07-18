package com.micatechnologies.minecraft.sum.huds;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests the pure HUD formatters extracted into {@link HudFormat} — the number/time/label math
 * that each HUD's {@code getText} used to inline. (DirectionHud's octant math is intentionally
 * NOT here: its rounding is under review for an off-by-one.)
 */
class HudFormatTest {

    // --- inGameTime ---

    @Test
    void inGameTime24Hour() {
        assertEquals("06:00", HudFormat.inGameTime(0, true));
        assertEquals("12:00", HudFormat.inGameTime(6000, true));
        assertEquals("18:00", HudFormat.inGameTime(12000, true));
        assertEquals("00:00", HudFormat.inGameTime(18000, true));
        assertEquals("05:00", HudFormat.inGameTime(23000, true));
    }

    @Test
    void inGameTime12Hour() {
        assertEquals("6:00 AM", HudFormat.inGameTime(0, false));
        assertEquals("12:00 PM", HudFormat.inGameTime(6000, false));
        assertEquals("6:00 PM", HudFormat.inGameTime(12000, false));
        assertEquals("12:00 AM", HudFormat.inGameTime(18000, false));
    }

    // --- memory ---

    @Test
    void memoryPercent() {
        assertEquals("50%", HudFormat.memory(100, 200, true));
        assertEquals("0%", HudFormat.memory(0, 200, true));
        assertEquals("100%", HudFormat.memory(200, 200, true));
    }

    @Test
    void memoryGigabytes() {
        assertEquals("1.9 / 6.0 GB", HudFormat.memory(2013265920L, 6442450944L, false));
    }

    // --- tps ---

    @Test
    void tpsClampsToTwenty() {
        assertEquals("20.00", HudFormat.tps(1000));
        assertEquals("10.00", HudFormat.tps(2000));
        assertEquals("20.00", HudFormat.tps(100), "a fast packet can't exceed 20 TPS");
        assertEquals("0.50", HudFormat.tps(40000));
    }

    // --- durability ---

    @Test
    void durabilityWithAndWithoutPercent() {
        assertEquals("1245", HudFormat.durability(1245, 1560, false));
        assertEquals("98 (98%)", HudFormat.durability(98, 100, true));
        assertEquals("0 (0%)", HudFormat.durability(0, 100, true));
        assertEquals("5 (0%)", HudFormat.durability(5, 0, true), "max 0 guards divide-by-zero");
    }

    // --- frameTime ---

    @Test
    void frameTimeFromFps() {
        assertEquals("17ms", HudFormat.frameTime(60));
        assertEquals("33ms", HudFormat.frameTime(30));
        assertEquals("1ms", HudFormat.frameTime(1000));
        assertEquals("?ms", HudFormat.frameTime(0));
        assertEquals("?ms", HudFormat.frameTime(-5));
    }

    // --- playtime ---

    @Test
    void playtimeFormatsHms() {
        assertEquals("01:23:45", HudFormat.playtime(5025));
        assertEquals("00:00:00", HudFormat.playtime(0));
        assertEquals("01:00:00", HudFormat.playtime(3600));
        assertEquals("23:59:59", HudFormat.playtime(86399));
        assertEquals("25:00:00", HudFormat.playtime(90000), "hours are not capped at 24");
    }

    // --- dayNumber ---

    @Test
    void dayNumberIsOneBased() {
        assertEquals(1, HudFormat.dayNumber(0));
        assertEquals(1, HudFormat.dayNumber(23999));
        assertEquals(2, HudFormat.dayNumber(24000));
        assertEquals(11, HudFormat.dayNumber(240000));
    }

    // --- borderDistance ---

    @Test
    void borderDistanceIsRadiusMinusMaxAxis() {
        assertEquals(5000, HudFormat.borderDistance(5000, 0, 0));
        assertEquals(1000, HudFormat.borderDistance(5000, 4000, -3000));
        assertEquals(1000, HudFormat.borderDistance(5000, 3000, 4000));
        assertEquals(-1000, HudFormat.borderDistance(5000, 6000, 0), "negative outside the border");
    }

    // --- loyalty ---

    @Test
    void loyaltyShowsCurrentAndNextMinutes() {
        assertEquals("12m / 30m", HudFormat.loyalty(14400, 36000));
        assertEquals("12m", HudFormat.loyalty(14400, -1), "no next milestone → current only");
        assertEquals("0m", HudFormat.loyalty(600, -1), "sub-minute ticks floor to 0m");
    }

    // --- xp ---

    @Test
    void xpWithAndWithoutProgress() {
        assertEquals("L30 (45%)", HudFormat.xp(30, 0.45f, true));
        assertEquals("L30", HudFormat.xp(30, 0.45f, false));
        assertEquals("L30 (100%)", HudFormat.xp(30, 0.999f, true));
        assertEquals("L0 (0%)", HudFormat.xp(0, 0f, true));
    }

    // --- plotInfo ---

    @Test
    void plotInfoAppendsOwnerWhenShown() {
        assertEquals("Apt 4 (alex)", HudFormat.plotInfo("Apt 4", "alex", true));
        assertEquals("Apt 4", HudFormat.plotInfo("Apt 4", "", true), "empty owner → name only");
        assertEquals("Apt 4", HudFormat.plotInfo("Apt 4", "alex", false));
    }

    // --- roomAbove ---

    @Test
    void roomAboveIsHeightMinusFlooredY() {
        assertEquals(192, HudFormat.roomAbove(256, 64.9));
        assertEquals(1, HudFormat.roomAbove(256, 255.0));
        assertEquals(256, HudFormat.roomAbove(256, 0.0));
    }
}
