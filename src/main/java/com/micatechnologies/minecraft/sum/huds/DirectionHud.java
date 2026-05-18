package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;

/**
 * Direction HUD — paints the cardinal direction the player is facing (N / E / S / W),
 * optionally with the full axis label ("North", "South-East", etc.). Modeled on
 * EvergreenHUD's direction element.
 */
public class DirectionHud extends SingleTextHud {

    @Switch(name = "Long Names")
    public boolean longNames = false;

    public DirectionHud() {
        super("Facing:", true, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return longNames ? "North-West" : "NW";
        }
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) {
            return "—";
        }
        float yaw = player.rotationYaw % 360F;
        if (yaw < 0F) yaw += 360F;
        // Map yaw to 8 octants (N, NE, E, SE, S, SW, W, NW). Vanilla puts yaw=0 facing
        // south, so rotate by +180 / 22.5° per octant.
        int octant = Math.round((yaw + 22.5F) / 45F) % 8;
        return longNames ? LONG[octant] : SHORT[octant];
    }

    /** Index 0..7 corresponding to yaw=0 (south), increasing clockwise per 45° step. */
    private static final String[] SHORT = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final String[] LONG = {
        "South", "South-West", "West", "North-West",
        "North", "North-East", "East", "South-East"
    };

    /** Bridge for any future caller that wants the EnumFacing equivalent. Not exposed in
     *  the GUI but kept here so other SUM features can reuse the same axis math. */
    public static EnumFacing horizontalFacing(EntityPlayer player) {
        return player.getHorizontalFacing();
    }
}
