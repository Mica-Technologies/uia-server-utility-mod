package com.micatechnologies.minecraft.sum.huds.snapshot;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * S→C carrier for {@link PlayerStatusSnapshot}. Registered on
 * {@link com.micatechnologies.minecraft.sum.atm.SumNetwork} at discriminator id 14.
 *
 * <p>Wire format is six packed fields — see {@link #toBytes} / {@link #fromBytes}.
 * Total payload is ~30 bytes for typical content; even at the 2s push interval
 * across a 50-player server that's well under 1KB/s of network use.</p>
 */
public class PacketSyncPlayerStatus implements IMessage {

    public PlayerStatusSnapshot snapshot;

    /** Required no-arg constructor for Forge's reflection-based packet construction. */
    public PacketSyncPlayerStatus() {}

    public PacketSyncPlayerStatus(PlayerStatusSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(snapshot.bankBalance);
        ByteBufUtils.writeUTF8String(buf, snapshot.plotName);
        ByteBufUtils.writeUTF8String(buf, snapshot.plotOwner);
        buf.writeInt(snapshot.jobsAvailable);
        buf.writeInt(snapshot.loyaltyTicks);
        buf.writeInt(snapshot.nextMilestoneTicks);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        long bankBalance = buf.readLong();
        String plotName = ByteBufUtils.readUTF8String(buf);
        String plotOwner = ByteBufUtils.readUTF8String(buf);
        int jobsAvailable = buf.readInt();
        int loyaltyTicks = buf.readInt();
        int nextMilestoneTicks = buf.readInt();
        this.snapshot = new PlayerStatusSnapshot(bankBalance, plotName, plotOwner,
            jobsAvailable, loyaltyTicks, nextMilestoneTicks);
    }

    public static class Handler implements IMessageHandler<PacketSyncPlayerStatus, IMessage> {

        @Override
        public IMessage onMessage(PacketSyncPlayerStatus msg, MessageContext ctx) {
            // Bounce onto the client main thread before mutating the static cache —
            // the network thread isn't allowed to touch client state directly.
            Minecraft.getMinecraft().addScheduledTask(
                () -> PlayerStatusTracker.receive(msg.snapshot));
            return null;
        }
    }
}
