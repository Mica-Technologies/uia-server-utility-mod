package com.micatechnologies.minecraft.sum.atm;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
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
            if (!EconomyBridge.adjustBalance(player, -denomination)) {
                tellError(player, "Withdraw failed.");
                return;
            }
            ItemStack stack = new ItemStack(bill, 1);
            if (!player.inventory.addItemStackToInventory(stack)) {
                player.dropItem(stack, false);
            }
            player.inventoryContainer.detectAndSendChanges();
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
            if (!EconomyBridge.adjustBalance(player, total)) {
                // Restoring the consumed bills here would require re-resolving denominations;
                // the most likely cause is a missing IMoney capability, in which case simply
                // letting the server log the failure and refunding via /sum econ add is fine.
                tellError(player, "Deposit failed; please contact an administrator.");
                Sum.LOGGER.warn("[atm] Deposit lost ${} for {} (no IMoney capability).",
                    total, player.getName());
                return;
            }
            player.inventoryContainer.detectAndSendChanges();
            player.sendMessage(new TextComponentString(
                TextFormatting.GREEN + "Deposited $" + total + "."));
        }

        private static void tellError(EntityPlayerMP player, String text) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + text));
        }
    }
}
