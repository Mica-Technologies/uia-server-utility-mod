package com.micatechnologies.minecraft.sum.economy;

import com.micatechnologies.minecraft.sum.api.event.WalletTransactionEvent;
import com.micatechnologies.minecraft.sum.atm.Bills;
import com.micatechnologies.minecraft.sum.pocket.PocketInventory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * The player's <b>wallet</b> — the money they carry, and what every in-game purchase spends.
 *
 * <p>A wallet is two things added together:
 *
 * <ul>
 *   <li>an <b>invisible balance</b>, the {@link ISumMoney} capability, which is what loyalty
 *       rewards, shop sales and payments settle against; and</li>
 *   <li>the face value of any <b>bill items</b> the player is carrying, in their inventory or
 *       their pocket's bills slot.</li>
 * </ul>
 *
 * <p>Counting bills matters because they are real purchasing power. A wallet readout that ignored
 * them would tell a player they have $50 while they stand there holding a $100 note, and refuse a
 * $120 purchase they can obviously afford.
 *
 * <p><b>The wallet is always local.</b> It lives in the world save and never touches the network,
 * so every purchase settles synchronously and cannot fail after the fact. The
 * {@link com.micatechnologies.minecraft.sum.bank.BankService bank} is the balance that may be
 * owned by a remote service; money moves between the two at an ATM.
 *
 * <p>Server-side. Reads are safe on either side, but {@link #spend} and {@link #credit} mutate
 * inventory and capabilities and must run on the server.
 */
public final class WalletService {

    private WalletService() {}

    /**
     * Total spendable value: the invisible balance plus every bill the player is carrying.
     *
     * @return the total in dollars, or {@link Double#NaN} if no balance backend is attached.
     */
    public static double getTotal(EntityPlayer player) {
        double invisible = getInvisibleBalance(player);
        if (Double.isNaN(invisible)) {
            return Double.NaN;
        }
        return invisible + getCarriedBillValue(player);
    }

    /** The abstract balance alone, excluding carried bills. */
    public static double getInvisibleBalance(EntityPlayer player) {
        return EconomyBridge.getBalance(player);
    }

    /** Face value of every bill in the player's inventory and pocket, in whole dollars. */
    public static long getCarriedBillValue(EntityPlayer player) {
        long total = 0L;
        for (BillStack bill : findBills(player)) {
            total += (long) bill.denomination * bill.stack.getCount();
        }
        return total;
    }

    /** True when the wallet can cover {@code amount} without touching the bank. */
    public static boolean canAfford(EntityPlayer player, double amount) {
        double total = getTotal(player);
        return !Double.isNaN(total) && total >= amount;
    }

    /**
     * Spends from the wallet, drawing the invisible balance down first and breaking carried bills
     * only for whatever it cannot cover.
     *
     * <p>Change from a broken note goes back into the invisible balance rather than being handed
     * out as smaller bills. It keeps the operation atomic — no second inventory insert that could
     * fail on a full inventory — and the value stays in the wallet either way, which is all the
     * player cares about.
     *
     * @return false if the wallet cannot cover the amount, in which case nothing is changed.
     */
    public static boolean spend(EntityPlayer player, double amount) {
        if (player == null || amount < 0.0) {
            return false;
        }
        if (amount == 0.0) {
            return true;
        }
        double invisible = getInvisibleBalance(player);
        if (Double.isNaN(invisible)) {
            return false;
        }
        if (invisible >= amount) {
            if (!EconomyBridge.adjustBalance(player, -amount)) {
                return false;
            }
            EconomyEventPoster.walletMoved(player, WalletTransactionEvent.Type.SPEND, amount,
                getTotal(player));
            return true;
        }

        // Cover the shortfall with bills, smallest first so the least value is broken up.
        double shortfall = amount - invisible;
        List<BillStack> bills = findBills(player);
        bills.sort(Comparator.comparingInt(b -> b.denomination));

        long consumed = 0L;
        List<BillStack> toConsume = new ArrayList<>();
        for (BillStack bill : bills) {
            for (int i = 0; i < bill.stack.getCount() && consumed < shortfall; i++) {
                consumed += bill.denomination;
                toConsume.add(bill);
            }
            if (consumed >= shortfall) {
                break;
            }
        }
        if (consumed < shortfall) {
            return false;
        }

        // Everything below this point must succeed: the funds have been verified.
        for (BillStack bill : toConsume) {
            bill.stack.shrink(1);
        }
        player.inventoryContainer.detectAndSendChanges();

        // The invisible balance is fully spent, then refilled with the change from the bills.
        double change = consumed - shortfall;
        if (!EconomyBridge.adjustBalance(player, change - invisible)) {
            return false;
        }
        EconomyEventPoster.walletMoved(player, WalletTransactionEvent.Type.SPEND, amount,
            getTotal(player));
        return true;
    }

    /** Credits the wallet's invisible balance. Used by loyalty rewards, sales and refunds. */
    public static boolean credit(EntityPlayer player, double amount) {
        if (player == null || amount < 0.0) {
            return false;
        }
        if (amount == 0.0) {
            return true;
        }
        if (!EconomyBridge.adjustBalance(player, amount)) {
            return false;
        }
        EconomyEventPoster.walletMoved(player, WalletTransactionEvent.Type.CREDIT, amount,
            getTotal(player));
        return true;
    }

    /** True when a wallet backend is attached at all. */
    public static boolean isAvailable() {
        return EconomyBridge.isAvailable();
    }

    /** A bill stack found on the player, paired with its denomination. */
    private static final class BillStack {
        final ItemStack stack;
        final int denomination;

        BillStack(ItemStack stack, int denomination) {
            this.stack = stack;
            this.denomination = denomination;
        }
    }

    /**
     * Every bill stack the player is carrying: main inventory (which includes the hotbar), the
     * offhand, and the pocket's dedicated bills slot.
     *
     * <p>Returns live {@link ItemStack} references so callers can shrink them in place.
     */
    private static List<BillStack> findBills(EntityPlayer player) {
        List<BillStack> found = new ArrayList<>();
        if (player == null) {
            return found;
        }
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int denomination = Bills.denominationOf(stack.getItem());
            if (denomination > 0) {
                found.add(new BillStack(stack, denomination));
            }
        }
        PocketInventory pocket = PocketInventory.get(player);
        if (pocket != null) {
            ItemStack stack = pocket.getStackInSlot(PocketInventory.Slot.BILLS.index);
            if (!stack.isEmpty()) {
                int denomination = Bills.denominationOf(stack.getItem());
                if (denomination > 0) {
                    found.add(new BillStack(stack, denomination));
                }
            }
        }
        return found;
    }

    /**
     * Pure arithmetic behind {@link #spend}: given an invisible balance and the bills available,
     * works out how much bill value must be broken and what change comes back.
     *
     * <p>Factored out so the ordering and change calculation can be unit-tested without a player.
     *
     * @param denominations available bill face values, one entry per individual note.
     * @return {@code [billValueConsumed, change]}, or null if the wallet cannot cover the amount.
     */
    static long[] planSpend(double amount, double invisibleBalance, int[] denominations) {
        if (amount <= 0.0 || invisibleBalance >= amount) {
            return new long[] { 0L, 0L };
        }
        double shortfall = amount - invisibleBalance;
        int[] sorted = denominations.clone();
        java.util.Arrays.sort(sorted);
        long consumed = 0L;
        for (int denomination : sorted) {
            if (consumed >= shortfall) {
                break;
            }
            consumed += denomination;
        }
        if (consumed < shortfall) {
            return null;
        }
        return new long[] { consumed, (long) Math.round((consumed - shortfall) * 100.0) };
    }
}
