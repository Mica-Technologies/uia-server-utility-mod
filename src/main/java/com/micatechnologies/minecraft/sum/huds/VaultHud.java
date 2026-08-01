package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import com.micatechnologies.minecraft.sum.huds.snapshot.PlayerStatusSnapshot;
import com.micatechnologies.minecraft.sum.huds.snapshot.PlayerStatusTracker;

/**
 * Total dollar value of currency bills the player has stored across every owned
 * safe-deposit box (in the player's current dimension). Reads from the snapshot
 * cache pushed by {@link PlayerStatusTracker}, so the value lags reality by up
 * to one snapshot interval (2 s) but costs nothing per render frame.
 */
public class BankBalanceHud extends SingleTextHud {

    public BankBalanceHud() {
        super("Bank", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) return "$12,345";
        PlayerStatusSnapshot snap = PlayerStatusTracker.latest;
        if (snap == null) return "—";
        return String.format("$%,d", snap.bankBalance);
    }
}
