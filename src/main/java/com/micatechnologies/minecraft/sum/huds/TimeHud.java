package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

/**
 * Time HUD — displays either the real-world local time or the in-game time-of-day.
 * Modeled on EvergreenHUD's time / Minecraft-time elements. Defaults to the in-game
 * clock in 12-hour AM/PM form, since the Alto layouts sit it beside the day counter
 * where players read it as "what time is it in-world".
 */
public class TimeHud extends SingleTextHud {

    @Switch(name = "Show In-Game Time (else: real time)")
    public boolean inGame = true;

    @Switch(name = "24-hour Format")
    public boolean twentyFourHour = false;

    private static final DateTimeFormatter FMT_24 = DateTimeFormatter.ofPattern("HH:mm");

    /** {@code h} not {@code hh}: matches {@link HudFormat#inGameTime}'s un-padded 12-hour clock. */
    private static final DateTimeFormatter FMT_12 = DateTimeFormatter.ofPattern("h:mm a");

    public TimeHud() {
        super("Time", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return twentyFourHour ? "13:42" : "1:42 PM";
        }
        if (inGame) {
            return formatInGameTime();
        }
        LocalTime now = LocalTime.now();
        return now.format(twentyFourHour ? FMT_24 : FMT_12);
    }

    /**
     * In-game day-time is 24000 ticks per day; tick 0 = 6:00 AM (vanilla sunrise). Convert
     * to a clock string so it lines up with how players read time on signs / clocks in-
     * world.
     */
    private String formatInGameTime() {
        World world = Minecraft.getMinecraft().world;
        if (world == null) {
            return "—";
        }
        return HudFormat.inGameTime(world.getWorldTime(), twentyFourHour);
    }
}
