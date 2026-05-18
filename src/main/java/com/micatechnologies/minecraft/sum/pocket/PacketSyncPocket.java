package com.micatechnologies.minecraft.sum.pocket;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server → client full snapshot of a player's pocket inventory. Sent on login, dimension
 * change, and after any server-side mutation (container interaction, command, etc.) so the
 * HUD overlay and any open {@link GuiPocket} always reflect server state.
 *
 * <p>The payload is the {@link PocketInventory}'s NBT — same blob the storage capability
 * persists — so adding a slot later automatically picks up here too.
 */
public class PacketSyncPocket implements IMessage {

    private net.minecraft.nbt.NBTTagCompound nbt;

    public PacketSyncPocket() {}

    public PacketSyncPocket(PocketInventory inventory) {
        this.nbt = inventory.serializeNBT();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.nbt = new PacketBuffer(buf).readCompoundTag();
        } catch (java.io.IOException e) {
            this.nbt = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        new PacketBuffer(buf).writeCompoundTag(this.nbt);
    }

    public static class Handler implements IMessageHandler<PacketSyncPocket, IMessage> {

        @Override
        public IMessage onMessage(PacketSyncPocket msg, MessageContext ctx) {
            // Marshal back onto the client main thread before touching the capability;
            // entity capabilities are not safe to mutate from the netty thread.
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (msg.nbt == null || Minecraft.getMinecraft().player == null) {
                    return;
                }
                PocketInventory inv = PocketInventory.get(Minecraft.getMinecraft().player);
                if (inv != null) {
                    inv.deserializeNBT(msg.nbt);
                }
            });
            return null;
        }
    }
}
