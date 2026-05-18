package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import com.micatechnologies.minecraft.sum.huds.snapshot.PlayerStatusSnapshot;
import com.micatechnologies.minecraft.sum.huds.snapshot.PlayerStatusTracker;

/**
 * Number of currently-open SUM job listings server-wide. The snapshot push
 * cadence (2 s) means the count lags the actual board state slightly, which is
 * fine — this HUD is for "is it worth walking to a job board" awareness, not
 * for rapid claim races.
 */
public class JobsHud extends SingleTextHud {

    public JobsHud() {
        super("Jobs:", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) return "7 open";
        PlayerStatusSnapshot snap = PlayerStatusTracker.latest;
        if (snap == null) return "—";
        return snap.jobsAvailable + " open";
    }
}
