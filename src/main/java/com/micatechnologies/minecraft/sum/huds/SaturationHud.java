package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Food saturation — vanilla's hidden hunger buffer that drains before the visible food
 * meter starts ticking down. Modeled on EvergreenHUD's saturation element.
 */
public class SaturationHud extends SingleTextHud {

    public SaturationHud() {
        super("Saturation:", true, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "5.0";
        }
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) {
            return "—";
        }
        return String.format("%.1f", player.getFoodStats().getSaturationLevel());
    }
}
