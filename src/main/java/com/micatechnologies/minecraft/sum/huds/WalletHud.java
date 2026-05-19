package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import com.micatechnologies.minecraft.sum.economy.CapabilitySumMoney;
import com.micatechnologies.minecraft.sum.economy.ISumMoney;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Player's current cash-on-hand balance, read from SUM's money capability
 * ({@link ISumMoney}). The capability auto-syncs C→S after every server-side
 * mutation, so this HUD always shows what the server thinks you have.
 */
public class WalletHud extends SingleTextHud {

    public WalletHud() {
        super("Wallet", false, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) return "$1,234.56";
        EntityPlayer p = Minecraft.getMinecraft().player;
        if (p == null) return "—";
        ISumMoney money = p.getCapability(CapabilitySumMoney.CAPABILITY, null);
        if (money == null) return "—";
        return String.format("$%,.2f", money.getBalance());
    }
}
