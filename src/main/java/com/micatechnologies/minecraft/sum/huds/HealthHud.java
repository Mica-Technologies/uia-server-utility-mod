package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Current / max HP. SUM ports Saturation and Armor separately but not the raw
 * Health value; this fills the obvious gap so survival / PvP layouts can show
 * a real HP readout without relying on the vanilla hearts overlay.
 */
public class HealthHud extends SingleTextHud {

    public HealthHud() {
        super("HP", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) return "20.0 / 20.0";
        EntityPlayer p = Minecraft.getMinecraft().player;
        if (p == null) return "—";
        return String.format("%.1f / %.1f", p.getHealth(), p.getMaxHealth());
    }
}
