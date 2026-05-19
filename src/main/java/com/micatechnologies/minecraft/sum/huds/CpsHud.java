package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;

/** Clicks per second (left-click). Reads from {@link HudStateTracker}. Modeled on
 *  EvergreenHUD's CPS element. */
public class CpsHud extends SingleTextHud {

    public CpsHud() {
        super("CPS", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "12";
        }
        return Long.toString(HudStateTracker.getClicksInLastSecond());
    }
}
