package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;

/** Lifetime click count (this session). Reads from {@link HudStateTracker}. Modeled on
 *  EvergreenHUD's click-counter ("CCounter") element. */
public class ClickCounterHud extends SingleTextHud {

    public ClickCounterHud() {
        super("Clicks", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "4,219";
        }
        return String.format("%,d", HudStateTracker.lifetimeClicks);
    }
}
