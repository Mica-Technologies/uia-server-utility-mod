package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import com.micatechnologies.minecraft.sum.economy.WalletService;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * The money the player is carrying: their invisible wallet balance plus the face value of every
 * bill in their inventory.
 *
 * <p>Both halves are readable client-side — the balance capability auto-syncs after every
 * server-side mutation, and the inventory is already there — so this needs no packet of its own
 * and stays exact between frames.
 *
 * <p>This is spending money. Savings held in a bank account are shown by {@link BankHud}.
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
        double total = WalletService.getTotal(p);
        if (Double.isNaN(total)) return "—";
        return String.format("$%,.2f", total);
    }
}
