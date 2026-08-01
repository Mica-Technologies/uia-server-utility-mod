package com.micatechnologies.minecraft.sum.atm;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.bank.BankService;
import com.micatechnologies.minecraft.sum.economy.WalletService;
import com.micatechnologies.minecraft.sum.omceapi.OmceParty;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client -&gt; server packet for ATM operations. Everything here moves money in or out of the
 * player's <b>bank account</b>; the ATM is the only place the bank and the wallet meet.
 *
 * <p>Four flows:
 *
 * <ul>
 *   <li>{@link #ACTION_DEPOSIT_WALLET} - wallet into the bank.</li>
 *   <li>{@link #ACTION_DEPOSIT_CASH} - every bill the player is carrying into the bank.</li>
 *   <li>{@link #ACTION_WITHDRAW_TO_WALLET} - bank into the wallet, spendable immediately.</li>
 *   <li>{@link #ACTION_WITHDRAW_CASH} - bank out as a physical note.</li>
 * </ul>
 *
 * <p>Turning wallet money into cash is deposit-then-withdraw-as-cash, which is why both withdraw
 * destinations exist.
 *
 * <p>{@link BankService} settles inline when the bank is local and after a round trip when a
 * remote economy owns it, so every grant here happens in a callback and nothing is handed over
 * before the money has actually moved.
 */
public class AtmPacketTransaction implements IMessage {

    /** Withdraw {@code amount} dollars from the bank as physical bills. Whole dollars only. */
    public static final int ACTION_WITHDRAW_CASH = 0;
    /** Deposit every bill the player is carrying into the bank. */
    public static final int ACTION_DEPOSIT_CASH = 1;
    /** Move {@code amount} dollars from the bank into the wallet. */
    public static final int ACTION_WITHDRAW_TO_WALLET = 2;
    /** Move {@code amount} dollars from the wallet into the bank; a non-positive amount
     *  means "everything". */
    public static final int ACTION_DEPOSIT_WALLET = 3;

    private int action;

    /** Dollars, not cents. A double because a player may type an arbitrary amount including
     *  cents; only cash withdrawals are constrained to whole dollars, and that is checked
     *  server-side. */
    private double amount;

    public AtmPacketTransaction() {
    }

    public AtmPacketTransaction(int action, double amount) {
        this.action = action;
        this.amount = amount;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readInt();
        this.amount = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.action);
        buf.writeDouble(this.amount);
    }

    int getAction() {
        return action;
    }

    double getAmount() {
        return amount;
    }

    public static class Handler implements IMessageHandler<AtmPacketTransaction, IMessage> {

        @Override
        public IMessage onMessage(AtmPacketTransaction msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            // IMessageHandler runs on the network thread; defer to the world thread before
            // touching the player's capabilities and inventory.
            player.getServer().addScheduledTask(() -> handle(player, msg));
            return null;
        }

        private void handle(EntityPlayerMP player, AtmPacketTransaction msg) {
            if (!BankService.isAvailable(player)) {
                tellError(player, "Your bank account isn't available right now.");
                return;
            }
            switch (msg.action) {
                case ACTION_WITHDRAW_CASH:
                    withdrawCash(player, msg.amount);
                    break;
                case ACTION_WITHDRAW_TO_WALLET:
                    withdrawToWallet(player, msg.amount);
                    break;
                case ACTION_DEPOSIT_CASH:
                    depositCash(player);
                    break;
                case ACTION_DEPOSIT_WALLET:
                    depositWallet(player, msg.amount);
                    break;
                default:
                    Sum.LOGGER.warn("[atm] unknown action {} from player {}", msg.action,
                        player.getName());
                    break;
            }
        }

        /**
         * Bank to physical notes. The bills are handed over only once the withdrawal has settled,
         * because an item in a player's inventory cannot reliably be taken back.
         *
         * <p>Any amount is allowed, not just a single denomination: the total is broken into notes
         * the same way a real cash machine would. Whole dollars only, since there is no sub-dollar
         * bill to pay the remainder with.
         */
        private void withdrawCash(EntityPlayerMP player, double amount) {
            if (amount <= 0.0) {
                tellError(player, "Enter an amount to withdraw.");
                return;
            }
            // Floor rather than round, so the suggested amount is never more than they asked for.
            // The epsilon absorbs float noise on a value that is really a whole number.
            int dollars = (int) Math.floor(amount + 0.0001);
            if (dollars < 1) {
                // Checked before the whole-dollar message, which would otherwise suggest "$0".
                tellError(player, "The smallest note is $1 — withdraw to your wallet instead.");
                return;
            }
            if (Math.abs(amount - dollars) > 0.0001) {
                tellError(player, "Cash comes in whole dollars — try $" + dollars
                    + ", or withdraw to your wallet instead.");
                return;
            }
            java.util.List<int[]> notes = Bills.breakIntoBills(dollars);
            if (notes.isEmpty()) {
                tellError(player, "That's too small to withdraw as cash.");
                return;
            }
            for (int[] pair : notes) {
                if (Bills.billItem(pair[0]) == null) {
                    tellError(player, "$" + pair[0] + " bills aren't available in this pack, so $"
                        + dollars + " can't be paid out in cash.");
                    return;
                }
            }
            BankService.withdraw(player, dollars, cashParty(player),
                "ATM withdrawal of $" + dollars + " in cash", result -> {
                    if (!result.ok) {
                        tellError(player, result.error);
                        return;
                    }
                    for (int[] pair : notes) {
                        giveBills(player, Bills.billItem(pair[0]), pair[1]);
                    }
                    tell(player, TextFormatting.GREEN, "Withdrew $" + dollars + " in cash.");
                });
        }

        /**
         * Bank to wallet. Nothing physical changes hands, but the credit still waits for the
         * withdrawal to settle.
         */
        private void withdrawToWallet(EntityPlayerMP player, double rawAmount) {
            double amount = roundToCents(rawAmount);
            if (amount <= 0.0) {
                tellError(player, "Enter an amount to withdraw.");
                return;
            }
            BankService.withdraw(player, amount, walletParty(player),
                "ATM withdrawal of $" + money(amount) + " to wallet", result -> {
                    if (!result.ok) {
                        tellError(player, result.error);
                        return;
                    }
                    if (!WalletService.credit(player, amount)) {
                        // The bank has already paid out, so failing to credit the wallet would
                        // destroy the money. Put it back and report that nothing happened.
                        Sum.LOGGER.error("[atm] Wallet credit of ${} for {} failed after the bank "
                            + "had settled; returning it to the account.", amount, player.getName());
                        BankService.deposit(player, amount, walletParty(player),
                            "Reversal: wallet credit failed", back -> { });
                        tellError(player, "Withdrawal failed; the money is still in your account.");
                        return;
                    }
                    tell(player, TextFormatting.GREEN,
                        "Moved $" + money(amount) + " to your wallet.");
                });
        }

        /** Every carried bill into the bank. Bills are taken first, so a refusal must return them. */
        private void depositCash(EntityPlayerMP player) {
            int total = 0;
            for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
                ItemStack stack = player.inventory.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                int denom = Bills.denominationOf(stack.getItem());
                if (denom <= 0) {
                    continue;
                }
                total += denom * stack.getCount();
                player.inventory.setInventorySlotContents(slot, ItemStack.EMPTY);
            }
            if (total <= 0) {
                tellError(player, "No bills found in your inventory.");
                return;
            }
            player.inventoryContainer.detectAndSendChanges();

            final int deposited = total;
            BankService.deposit(player, deposited, cashParty(player), "ATM cash deposit",
                result -> {
                    if (!result.ok) {
                        returnDepositedBills(player, deposited);
                        tellError(player, result.error);
                        return;
                    }
                    tell(player, TextFormatting.GREEN, "Deposited $" + deposited + " in cash.");
                });
        }

        /** Wallet into the bank. A non-positive amount deposits the whole wallet. */
        private void depositWallet(EntityPlayerMP player, double rawAmount) {
            double available = WalletService.getTotal(player);
            if (Double.isNaN(available) || available <= 0.0) {
                tellError(player, "Your wallet is empty.");
                return;
            }
            double requested = roundToCents(rawAmount);
            if (requested > available) {
                tellError(player, "Your wallet only holds $" + money(available) + ".");
                return;
            }
            // Capped at the wallet total so "deposit everything" stays a single click, and a
            // rounding artefact on a typed amount can never ask for more than is there.
            double toDeposit = requested <= 0.0 ? available : Math.min(requested, available);
            if (!WalletService.spend(player, toDeposit)) {
                tellError(player, "Your wallet could not cover $" + money(toDeposit) + ".");
                return;
            }
            final double moved = toDeposit;
            BankService.deposit(player, moved, walletParty(player), "ATM wallet deposit",
                result -> {
                    if (!result.ok) {
                        // The wallet has already been debited; give it back.
                        WalletService.credit(player, moved);
                        tellError(player, result.error);
                        return;
                    }
                    tell(player, TextFormatting.GREEN,
                        "Deposited $" + money(moved) + " from your wallet.");
                });
        }

        /**
         * Hands back the value of a cash deposit whose credit was refused.
         *
         * <p>Denominations are re-derived from the total, which is exact because every bill is a
         * whole-dollar denomination - the player may get a different mix of notes, never a
         * different amount.
         */
        private static void returnDepositedBills(EntityPlayerMP player, int total) {
            Sum.LOGGER.warn("[atm] Cash deposit of ${} for {} was refused; returning the bills.",
                total, player.getName());
            for (int[] pair : Bills.breakIntoBills(total)) {
                Item denomItem = Bills.billItem(pair[0]);
                if (denomItem != null) {
                    giveBills(player, denomItem, pair[1]);
                }
            }
        }

        /** Adds {@code count} bills as a single stack, dropping it if the inventory is full. */
        private static void giveBills(EntityPlayerMP player, Item bill, int count) {
            if (count <= 0) {
                return;
            }
            ItemStack stack = new ItemStack(bill, count);
            if (!player.inventory.addItemStackToInventory(stack)) {
                player.dropItem(stack, false);
            }
            player.inventoryContainer.detectAndSendChanges();
        }

        /**
         * Physical cash as a transaction counterparty for a remote ledger. Virtual: the service
         * never sees the bill items, only the balance side of the exchange.
         */
        private static OmceParty cashParty(EntityPlayerMP player) {
            String worldName = (player.world == null || player.world.provider == null)
                ? "world"
                : player.world.provider.getDimensionType().getName();
            return OmceParty.cash(OmceParty.positionId("atm", worldName,
                (int) player.posX, (int) player.posY, (int) player.posZ));
        }

        /** The wallet as a counterparty. Also virtual: SUM owns the wallet, not the service. */
        private static OmceParty walletParty(EntityPlayerMP player) {
            return OmceParty.system("wallet." + player.getUniqueID());
        }

        /** Rounds a typed amount to whole cents, so float noise never reaches a balance. */
        private static double roundToCents(double value) {
            return Math.floor(value * 100.0 + 0.5) / 100.0;
        }

        private static String money(double value) {
            return String.format(Locale.ROOT, "%,.2f", value);
        }

        private static void tell(EntityPlayerMP player, TextFormatting color, String text) {
            player.sendMessage(new TextComponentString(color + text));
        }

        private static void tellError(EntityPlayerMP player, String text) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + text));
        }
    }
}
