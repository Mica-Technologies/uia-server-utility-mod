package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;

/** Blocks placed this session. Reads from {@link HudStateTracker}. Modeled on
 *  EvergreenHUD's place-count element. */
public class PlaceCountHud extends SingleTextHud {

    public PlaceCountHud() {
        super("Placed", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "1,024";
        }
        return String.format("%,d", HudStateTracker.lifetimePlacedBlocks);
    }
}
