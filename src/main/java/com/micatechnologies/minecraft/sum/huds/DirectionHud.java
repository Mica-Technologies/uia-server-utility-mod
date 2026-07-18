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
        super("Facing", false, 5, 5);
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
        return HudFormat.direction(player.rotationYaw, longNames);
    }

    /** Bridge for any future caller that wants the EnumFacing equivalent. Not exposed in
     *  the GUI but kept here so other SUM features can reuse the same axis math. */
    public static EnumFacing horizontalFacing(EntityPlayer player) {
        return player.getHorizontalFacing();
    }
}
