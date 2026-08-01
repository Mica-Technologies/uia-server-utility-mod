package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import com.micatechnologies.minecraft.sum.huds.snapshot.PlayerStatusSnapshot;
import com.micatechnologies.minecraft.sum.huds.snapshot.PlayerStatusTracker;

/**
 * The player's bank account balance — savings held away from their person, reached at an ATM or
 * with a debit card.
 *
 * <p>Unlike {@link WalletHud}, this cannot be computed client-side: the account may be owned by a
 * remote economy service the client has no access to, so the value arrives in the periodic status
 * snapshot and lags by up to one interval (2 s).
 *
 * <p>Not to be confused with {@link VaultHud}, which counts bills stored in safe-deposit boxes.
 */
public class BankHud extends SingleTextHud {

    public BankHud() {
        super("Bank", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) return "$12,345.00";
        PlayerStatusSnapshot snap = PlayerStatusTracker.latest;
        if (snap == null || Double.isNaN(snap.bankBalance)) return "—";
        return String.format("$%,.2f", snap.bankBalance);
    }
}
