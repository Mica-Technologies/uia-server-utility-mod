package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.config.annotations.Dropdown;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Horizontal movement speed in either blocks-per-second or blocks-per-tick. Modeled on
 * EvergreenHUD's speed element.
 */
public class SpeedHud extends SingleTextHud {

    @Dropdown(name = "Unit", options = {"blocks/sec", "blocks/tick"})
    public int unit = 0;

    public SpeedHud() {
        super("Speed:", true, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return unit == 0 ? "5.40 b/s" : "0.27 b/t";
        }
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) {
            return "—";
        }
        double dx = player.posX - player.prevPosX;
        double dz = player.posZ - player.prevPosZ;
        double perTick = Math.sqrt(dx * dx + dz * dz);
        if (unit == 0) {
            return String.format("%.2f b/s", perTick * 20.0);
        }
        return String.format("%.2f b/t", perTick);
    }
}
