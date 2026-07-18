package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.client.Minecraft;

/**
 * Frame-time HUD — milliseconds per frame, derived from Minecraft's own debug FPS
 * counter (the same field F3 reads). Inverse of {@link FpsHud}: where FPS asks
 * "frames per second", FTI asks "ms per frame", which is the more useful number when
 * comparing GPU/CPU budgets between scenes.
 */
public class FtiHud extends SingleTextHud {

    public FtiHud() {
        super("FTI", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "7ms";
        }
        return HudFormat.frameTime(Minecraft.getDebugFPS());
    }
}
