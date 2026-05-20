package com.micatechnologies.minecraft.sum.serverconfig;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Server→client: deliver a snapshot of the server's {@code sum.cfg} runtime state. Sent
 * on {@link net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent}
 * (per-player one-shot). Client stashes it in {@link ServerConfigMirror#latest} for the
 * OneConfig viewer to surface.
 */
public class PacketSyncServerConfig implements IMessage {

    private ServerConfigSnapshot snapshot;

    public PacketSyncServerConfig() {}

    public PacketSyncServerConfig(ServerConfigSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.snapshot = ServerConfigSnapshot.readFrom(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        this.snapshot.writeTo(buf);
    }

    public static class Handler implements IMessageHandler<PacketSyncServerConfig, IMessage> {

        @Override
        public IMessage onMessage(PacketSyncServerConfig msg, MessageContext ctx) {
            // Registered Side.CLIENT — bounce through a client-only inner class so the
            // server classloader never resolves Minecraft when SumNetwork.init() runs.
            ClientHandler.handle(msg);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static class ClientHandler {
            static void handle(PacketSyncServerConfig msg) {
                net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(
                    () -> ServerConfigMirror.receive(msg.snapshot));
            }
        }
    }
}
