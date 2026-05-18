package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/** Vertical look angle. Modeled on EvergreenHUD's pitch element. */
public class PitchHud extends SingleTextHud {

    public PitchHud() {
        super("Pitch:", true, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "-12.3°";
        }
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) {
            return "—";
        }
        // Normalize to (-180, 180]; vanilla can produce values outside the expected
        // range during teleports / portals.
        float pitch = player.rotationPitch;
        return String.format("%.1f°", pitch);
    }
}
