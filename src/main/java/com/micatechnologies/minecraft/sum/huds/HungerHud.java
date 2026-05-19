package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Food level (0-20). Separate from {@link SaturationHud} because the two values
 * answer different questions — hunger decides whether you can sprint, saturation
 * decides how soon you'll need to eat.
 */
public class HungerHud extends SingleTextHud {

    public HungerHud() {
        super("Food", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) return "20 / 20";
        EntityPlayer p = Minecraft.getMinecraft().player;
        if (p == null) return "—";
        return p.getFoodStats().getFoodLevel() + " / 20";
    }
}
