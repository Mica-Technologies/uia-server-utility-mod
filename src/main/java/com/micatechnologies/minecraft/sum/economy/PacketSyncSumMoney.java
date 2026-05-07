package com.micatechnologies.minecraft.sum.economy;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Server -> client packet that pushes the player's current SUM balance to their client. Sent
 * on login, after dimension change, and after each server-side balance mutation.
 *
 * <p>EconomyInc has its own {@code PacketMoneyData} for the same purpose; the SUM bridge
 * doesn't sync EconomyInc's capability (that's EconomyInc's job). This packet only carries
 * SUM's own balance.
 */
public class PacketSyncSumMoney implements IMessage {

    private double balance;

    public PacketSyncSumMoney() {
    }

    public PacketSyncSumMoney(double balance) {
        this.balance = balance;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.balance = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(this.balance);
    }

    public static class Handler implements IMessageHandler<PacketSyncSumMoney, IMessage> {

        @Override
        public IMessage onMessage(PacketSyncSumMoney msg, MessageContext ctx) {
            // Registered Side.CLIENT, so this only fires on the client thread (or rather, the
            // network thread - schedule a task back to the client thread before touching the
            // capability so we're not racing the renderer).
            ClientHandler.handle(msg);
            return null;
        }

        /** Inner class so that touching {@code Minecraft} only happens on the client classloader.
         *  The outer {@code Handler} class can be loaded server-side (e.g. when SumNetwork.init()
         *  references it during preInit) without pulling in client-only types. */
        @SideOnly(Side.CLIENT)
        private static class ClientHandler {
            static void handle(PacketSyncSumMoney msg) {
                Minecraft mc = Minecraft.getMinecraft();
                mc.addScheduledTask(() -> {
                    EntityPlayer player = mc.player;
                    if (player == null) return;
                    ISumMoney money = player.getCapability(CapabilitySumMoney.CAPABILITY, null);
                    if (money != null) {
                        money.setBalance(msg.balance);
                    }
                });
            }
        }
    }
}
