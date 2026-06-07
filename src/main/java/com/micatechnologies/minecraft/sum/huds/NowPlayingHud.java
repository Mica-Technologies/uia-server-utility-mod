package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import com.micatechnologies.minecraft.sum.music.NowPlayingTracker;

/**
 * Now Playing HUD — shows the title of the background-music track (and optionally the
 * nearest spinning jukebox record) currently playing, as captured by
 * {@link NowPlayingTracker}. Survival music gaps are minutes long, so by default the
 * element hides itself entirely while nothing is playing instead of parking an empty
 * box on screen.
 */
public class NowPlayingHud extends SingleTextHud {

    @Switch(name = "Hide When Nothing Playing")
    public boolean hideWhenSilent = true;

    @Switch(name = "Include Music Discs")
    public boolean includeRecords = true;

    public NowPlayingHud() {
        super("Now Playing", false, 5, 50);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "Uptown Vibes";
        }
        String title = NowPlayingTracker.getNowPlayingTitle(includeRecords);
        return title != null ? title : "—";
    }

    @Override
    protected boolean shouldShow() {
        if (hideWhenSilent && NowPlayingTracker.getNowPlayingTitle(includeRecords) == null) {
            return false;
        }
        return super.shouldShow();
    }
}
