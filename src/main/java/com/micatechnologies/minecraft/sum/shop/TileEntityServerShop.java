package com.micatechnologies.minecraft.sum.shop;

import net.minecraft.entity.player.EntityPlayer;

/**
 * State for {@link BlockServerShop}: a shop with no player owner. Everything about the sale —
 * template slot, 3x3 stock grid, sale amount, price, infinite-stock flag — behaves exactly as
 * it does for a player shop; only the two roles change.
 *
 * <p><b>Who may configure it.</b> Operators (permission level 2, node
 * {@link #MANAGE_PERMISSION_NODE}), which on a single-player world means the world owner with
 * cheats enabled. Nobody claims the block, so it is never "owned" and
 * {@link #getOwnerName()} stays empty.
 *
 * <p><b>Who pays.</b> Everyone, operators included. Being able to set the shop up does not
 * exempt anyone from the price — that is the whole point of the block, so
 * {@link #canPurchase} is unconditionally true and the "you can't buy from your own shop"
 * refusal never fires here.
 *
 * <p><b>Where the money goes.</b> Nowhere. With no owner to pay, takings are destroyed rather
 * than banked, which makes a server shop a currency sink: {@link #depositFunds} is a no-op and
 * there is nothing to withdraw. The owner GUI hides its till readout and Withdraw button
 * accordingly.
 */
public class TileEntityServerShop extends TileEntityShop {

    /** Vanilla op level required to configure a server shop. */
    public static final int MANAGE_PERMISSION_LEVEL = 2;

    /** Permission node a permissions mod can grant/revoke independently of op level. */
    public static final String MANAGE_PERMISSION_NODE = "sum.server_shop.manage";

    @Override
    public boolean isServerShop() {
        return true;
    }

    @Override
    public boolean canManage(EntityPlayer player) {
        return player.canUseCommand(MANAGE_PERMISSION_LEVEL, MANAGE_PERMISSION_NODE);
    }

    /** Operators set the shop up but still pay for what they take out of it. */
    @Override
    public boolean canPurchase(EntityPlayer player) {
        return true;
    }

    /** No owner to credit — the payment leaves circulation. */
    @Override
    protected void depositFunds(double amount) {
    }

    @Override
    public double withdrawFunds(EntityPlayer player) {
        return 0.0;
    }

    @Override
    public String getName() {
        return "sum.server_shop.title";
    }
}
