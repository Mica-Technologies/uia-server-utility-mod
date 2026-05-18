package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import com.micatechnologies.minecraft.sum.huds.snapshot.PlayerStatusSnapshot;
import com.micatechnologies.minecraft.sum.huds.snapshot.PlayerStatusTracker;

/**
 * Name (and optionally owner) of the SUM plot the player is currently inside.
 * Reads from the periodic snapshot pushed by {@link PlayerStatusTracker} — plot
 * transitions feel snappy in the 2-second worst case but aren't instantaneous.
 *
 * <p>Renders "—" when standing outside any plot. The HUD itself doesn't try to
 * be clever about overlapping plots; the snapshot picks whichever plot is
 * returned first by {@code getPlotsContaining()}.</p>
 */
public class PlotInfoHud extends SingleTextHud {

    /** Append the plot owner in parens after the name. */
    @Switch(name = "Show owner")
    public boolean showOwner = true;

    public PlotInfoHud() {
        super("Plot:", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) return showOwner ? "Downtown Apt 4 (alex)" : "Downtown Apt 4";
        PlayerStatusSnapshot snap = PlayerStatusTracker.latest;
        if (snap == null || snap.plotName.isEmpty()) return "—";
        if (!showOwner || snap.plotOwner.isEmpty()) return snap.plotName;
        return snap.plotName + " (" + snap.plotOwner + ")";
    }
}
