package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

/**
 * Time HUD — displays either the real-world local time or the in-game time-of-day.
 * Modeled on EvergreenHUD's time / Minecraft-time elements.
 */
public class TimeHud extends SingleTextHud {

    @Switch(name = "Show In-Game Time (else: real time)")
    public boolean inGame = false;

    @Switch(name = "24-hour Format")
    public boolean twentyFourHour = true;

    private static final DateTimeFormatter FMT_24 = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FMT_12 = DateTimeFormatter.ofPattern("hh:mm a");

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
