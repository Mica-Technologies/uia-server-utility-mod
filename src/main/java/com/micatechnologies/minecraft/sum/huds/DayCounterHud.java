package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

/**
 * Day counter — shows the in-game day number (world ticks / 24000 + 1). Modeled on
 * EvergreenHUD's day-counter element.
 */
public class DayCounterHud extends SingleTextHud {

    public DayCounterHud() {
        super("Day", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "42";
        }
        World world = Minecraft.getMinecraft().world;
        if (world == null) {
            return "—";
        }
        long day = world.getWorldTime() / 24000L + 1L;
        return Long.toString(day);
    }
}
