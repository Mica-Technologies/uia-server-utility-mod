package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import com.micatechnologies.minecraft.sum.huds.snapshot.PlayerStatusSnapshot;
import com.micatechnologies.minecraft.sum.huds.snapshot.PlayerStatusTracker;

/**
 * Lifetime SUM loyalty progress — currently accrued ticks plus progress toward
 * the next milestone. Loyalty ticks accumulate one per server tick while the
 * player is online (so 1200 ticks = 1 in-game minute of playtime); milestones
 * are configured in {@code sum.cfg}. See {@code LoyaltyHandler} for the source
 * of truth.
 *
 * <p>Formats as "12m / 30m" (current minutes / next-milestone minutes) for
 * legibility — raw tick counts get unwieldy fast.</p>
 */
public class LoyaltyHud extends SingleTextHud {

    public LoyaltyHud() {
        super("Loyalty", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) return "12m / 30m";
        PlayerStatusSnapshot snap = PlayerStatusTracker.latest;
        if (snap == null) return "—";
        return HudFormat.loyalty(snap.loyaltyTicks, snap.nextMilestoneTicks);
    }
}
