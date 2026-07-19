package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.config.annotations.Dropdown;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Horizontal movement speed in miles-per-hour, km/h, blocks-per-second or blocks-per-tick.
 * Modeled on EvergreenHUD's speed element, which offers the same real-world units.
 */
public class SpeedHud extends SingleTextHud {

    /** A representative walking-ish speed, used to render the layout-editor preview. */
    private static final double EXAMPLE_PER_TICK = 0.27;

    /**
     * Dropdown order must match the {@code HudFormat.SPEED_*} indices, which are persisted by
     * ordinal — mph and km/h are appended so existing saved selections keep their meaning.
     */
    @Dropdown(name = "Unit", options = {"blocks/sec", "blocks/tick", "mph", "km/h"})
    public int unit = HudFormat.SPEED_MPH;

    public SpeedHud() {
        super("Speed", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return HudFormat.speed(EXAMPLE_PER_TICK, unit);
        }
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) {
            return "—";
        }
        double dx = player.posX - player.prevPosX;
        double dz = player.posZ - player.prevPosZ;
        return HudFormat.speed(Math.sqrt(dx * dx + dz * dz), unit);
    }
}
