package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Coordinates HUD — shows the player's X, Y, Z. Modeled on EvergreenHUD's coords
 * element. Per-HUD options (color the values, hide the Y axis, etc.) live on this class
 * and are auto-surfaced by OneConfig under the HUD's settings page.
 */
public class CoordsHud extends SingleTextHud {

    @Switch(name = "Show Y")
    public boolean showY = true;

    public CoordsHud() {
        super("XYZ", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return showY ? "100 / 64 / -250" : "100 / -250";
        }
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) {
            return "—";
        }
        int x = (int) Math.floor(player.posX);
        int y = (int) Math.floor(player.posY);
        int z = (int) Math.floor(player.posZ);
        return showY ? x + " / " + y + " / " + z : x + " / " + z;
    }
}
