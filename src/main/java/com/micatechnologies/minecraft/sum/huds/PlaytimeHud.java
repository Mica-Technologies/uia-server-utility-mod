package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;

/** Current play-session length in HH:MM:SS, reset on logout / world-leave. Reads from
 *  {@link HudStateTracker}. Modeled on EvergreenHUD's playtime element. */
public class PlaytimeHud extends SingleTextHud {

    public PlaytimeHud() {
        super("Played", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "01:23:45";
        }
        long start = HudStateTracker.sessionStartMillis;
        if (start == 0L) {
            return "—";
        }
        long elapsedSec = (System.currentTimeMillis() - start) / 1000L;
        return HudFormat.playtime(elapsedSec);
    }
}
