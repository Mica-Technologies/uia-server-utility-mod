package com.micatechnologies.minecraft.sum.atm;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import com.micatechnologies.minecraft.sum.omceapi.OmceMoney;
import com.micatechnologies.minecraft.sum.omceapi.OmceParty;
import com.micatechnologies.minecraft.sum.omceapi.OmceProtocol;
import com.micatechnologies.minecraft.sum.omceapi.OmceTransactionRequest;
import com.micatechnologies.minecraft.sum.omceapi.service.OmceEconomyService;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client -> server packet for ATM withdraw and deposit operations. Sent when the player clicks
 * a button in the ATM GUI. The server validates the request (player exists, EconomyInc is
 * available, balance/inventory permits the operation), mutates state, and {@code sync()}s the
 * IMoney capability so the client sees the new balance on the next frame.
 */
public class AtmPacketTransaction implements IMessage {

    public static final int ACTION_WITHDRAW = 0;
    public static final int ACTION_DEPOSIT_ALL = 1;

    private int action;
    private int amount;

    public AtmPacketTransaction() {
    }

    public AtmPacketTransaction(int action, int amount) {
        this.action = action;
        this.amount = amount;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readInt();
        this.amount = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.action);
        buf.writeInt(this.amount);
    }

    int getAction() {
        return action;
    }

    int getAmount() {
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
            if (!EconomyBridge.isAvailable()) {
                tellError(player, "Economy mod is not loaded.");
                return;
            }
            switch (msg.action) {
                case ACTION_WITHDRAW:
                    handleWithdraw(player, msg.amount);
                    break;
                case ACTION_DEPOSIT_ALL:
                    handleDepositAll(player);
                    break;
                default:
                    Sum.LOGGER.warn("[atm] unknown action {} from player {}", msg.action, player.getName());
                    break;
            }
        }

        private void handleWithdraw(EntityPlayerMP player, int denomination) {
            Item bill = Bills.billItem(denomination);
            if (bill == null) {
                tellError(player, "$" + denomination + " bill is not available in this pack.");
                return;
            }
            double balance = EconomyBridge.getBalance(player);
            if (Double.isNaN(balance)) {
                tellError(player, "No money handler attached to your player.");
                return;
            }
            if (balance < denomination) {
                tellError(player, "Insufficient funds: balance is $"
                    + String.format(java.util.Locale.ROOT, "%.2f", balance) + ".");
                return;
            }
            OmceEconomyService remote = EconomyBridge.getRemoteService();
            if (remote != null) {
                // A withdrawn bill cannot be taken back, so it must not be issued on an
                // optimistic write. Charge first and hand over the item only once the service
                // reports the transaction committed.
                withdrawViaRemote(remote, player, bill, denomination);
                return;
            }
            if (!EconomyBridge.adjustBalance(player, -denomination,
                OmceProtocol.TX_ATM_WITHDRAW, cashParty(player), "ATM withdrawal")) {
                tellError(player, "Withdraw failed.");
                return;
            }
            giveBill(player, bill);
        }

        private void handleDepositAll(EntityPlayerMP player) {
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
            final int deposited = total;
            // The bills are already out of the inventory, so a refused credit must hand back
            // equivalent value rather than destroying it. This has to cover both failure shapes:
            // an up-front refusal (the boolean below) and a remote economy refusing after the
            // optimistic write, which arrives via the rejection callback.
            if (!EconomyBridge.adjustBalance(player, total,
                OmceProtocol.TX_ATM_DEPOSIT, cashParty(player), "ATM deposit",
                () -> returnDepositedBills(player, deposited))) {
                returnDepositedBills(player, deposited);
                return;
            }
            player.inventoryContainer.detectAndSendChanges();
            player.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "Deposited $" + total + "."));
        }

        /**
         * Charges the player and hands over the bill only on a committed transaction.
         *
         * <p>The callback runs on the server thread, so touching the inventory here is safe.
         */
        private void withdrawViaRemote(OmceEconomyService remote, EntityPlayerMP player,
            Item bill, int denomination) {
            long amount = OmceMoney.priceToMinorUnits(denomination, remote.getMinorUnitDigits());
            OmceTransactionRequest request = OmceTransactionRequest.builder(
                    OmceProtocol.TX_ATM_WITHDRAW, amount,
                    OmceParty.player(player.getUniqueID(), player.getName(), null),
                    cashParty(player))
                .initiator(player.getUniqueID(), player.getName(),
                    OmceTransactionRequest.ROLE_PLAYER)
                .reason("ATM withdrawal of $" + denomination)
                .meta("denomination", Integer.toString(denomination))
                .build();

            player.sendMessage(new TextComponentString(
                TextFormatting.GRAY + "Processing withdrawal..."));

            remote.processTransaction(request, result -> {
                if (result.isFailure() || !result.get().isCommitted()) {
                    tellError(player, result.isFailure()
                        ? result.getError().playerMessage(remote.getMinorUnitDigits(),
                            remote.getCurrencySymbol())
                        : "Withdrawal was not completed.");
                    return;
                }
                giveBill(player, bill);
                player.sendMessage(new TextComponentString(
                    TextFormatting.GREEN + "Withdrew $" + denomination + "."));
            });
        }

        /**
         * Hands back the value of a deposit whose credit was refused.
         *
         * <p>Denominations are re-derived from the total, which is exact because every bill is a
         * whole-dollar denomination — the player may not get back the same mix of notes they put
         * in, but never a different amount.
         */
        private static void returnDepositedBills(EntityPlayerMP player, int total) {
            Sum.LOGGER.warn("[atm] Deposit of ${} for {} was refused; returning the bills.",
                total, player.getName());
            for (int[] pair : Bills.breakIntoBills(total)) {
                Item denomItem = Bills.billItem(pair[0]);
                if (denomItem != null) {
                    // breakIntoBills already caps each pair at a full stack, so this is one
                    // ItemStack per pair rather than one per bill.
                    giveBills(player, denomItem, pair[1]);
                }
            }
            tellError(player, "Deposit failed; your bills were returned.");
        }

        /** Adds one bill to the player's inventory, dropping it at their feet if there's no room. */
        private static void giveBill(EntityPlayerMP player, Item bill) {
            giveBills(player, bill, 1);
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
         * The physical-cash counterparty for this ATM. Virtual: the service never sees the bill
         * items, only the balance side of the exchange.
         */
        private static OmceParty cashParty(EntityPlayerMP player) {
            String worldName = (player.world == null || player.world.provider == null)
                ? "world"
                : player.world.provider.getDimensionType().getName();
            return OmceParty.cash(OmceParty.positionId("atm", worldName,
                (int) player.posX, (int) player.posY, (int) player.posZ));
        }

        private static void tellError(EntityPlayerMP player, String text) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + text));
        }
    }
}
