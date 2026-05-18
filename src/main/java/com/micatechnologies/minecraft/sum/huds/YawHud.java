package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/** Horizontal look angle (raw degrees). Modeled on EvergreenHUD's yaw element. */
public class YawHud extends SingleTextHud {

    public YawHud() {
        super("Yaw:", true, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "82.7°";
        }
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) {
            return "—";
        }
        float yaw = player.rotationYaw % 360F;
        if (yaw < 0F) yaw += 360F;
        return String.format("%.1f°", yaw);
    }
}
