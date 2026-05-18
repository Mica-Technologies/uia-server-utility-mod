package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;

/**
 * FPS HUD — reads Minecraft's debug-overlay FPS field (which is what F3 displays).
 * Modeled on EvergreenHUD's FPS element.
 */
public class FpsHud extends SingleTextHud {

    public FpsHud() {
        super("FPS:", true, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "240";
        }
        return Integer.toString(Minecraft.getDebugFPS());
    }
}
