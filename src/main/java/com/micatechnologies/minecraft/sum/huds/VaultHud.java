package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import com.micatechnologies.minecraft.sum.huds.snapshot.PlayerStatusSnapshot;
import com.micatechnologies.minecraft.sum.huds.snapshot.PlayerStatusTracker;

/**
 * Total face value of currency bills the player has stored across every owned safe-deposit box in
 * their current dimension.
 *
 * <p>This is <b>stored cash</b>, not an account: it counts bill items sitting in vault inventories.
 * The player's actual account balance is {@link BankHud}, and the money they are carrying is
 * {@link WalletHud}.
 *
 * <p>Reads from the snapshot cache pushed by {@link PlayerStatusTracker}, so the value lags reality
 * by up to one snapshot interval (2 s) but costs nothing per render frame.
 */
public class VaultHud extends SingleTextHud {

    public VaultHud() {
        super("Vault", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) return "$12,345";
        PlayerStatusSnapshot snap = PlayerStatusTracker.latest;
        if (snap == null) return "—";
        return String.format("$%,d", snap.vaultBillValue);
    }
}
