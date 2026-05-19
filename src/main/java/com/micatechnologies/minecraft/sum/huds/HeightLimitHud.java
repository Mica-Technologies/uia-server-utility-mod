package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * Distance from the player to the world's build height cap. Useful for sky-base builds
 * where "how much vertical room do I have" decides whether the next column fits.
 * Modeled on EvergreenHUD's height-limit element.
 */
public class HeightLimitHud extends SingleTextHud {

    @Switch(name = "Show Absolute Limit")
    public boolean absolute = false;

    public HeightLimitHud() {
        super("Height Limit", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return absolute ? "256" : "192";
        }
        EntityPlayer player = Minecraft.getMinecraft().player;
        World world = Minecraft.getMinecraft().world;
        if (player == null || world == null) {
            return "—";
        }
        int limit = world.getHeight();
        if (absolute) {
            return Integer.toString(limit);
        }
        return Integer.toString(limit - (int) Math.floor(player.posY));
    }
}
