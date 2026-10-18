package com.micatechnologies.minecraft.sum.jail;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Server→client: open (or refresh) the Jail Control Tool's screen with a {@link JailControlSnapshot}.
 * Sent when the tool is used, and again after every action so the open screen shows the result.
 */
public class PacketOpenJailControl implements IMessage {

    private JailControlSnapshot snapshot;

    public PacketOpenJailControl() {}

    PacketOpenJailControl(JailControlSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        snapshot = JailControlSnapshot.readFrom(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        snapshot.writeTo(buf);
    }

    public static class Handler implements IMessageHandler<PacketOpenJailControl, IMessage> {

        @Override
        public IMessage onMessage(PacketOpenJailControl msg, MessageContext ctx) {
            ClientHandler.handle(msg);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static class ClientHandler {
            static void handle(PacketOpenJailControl msg) {
                Minecraft mc = Minecraft.getMinecraft();
                mc.addScheduledTask(() -> {
                    if (mc.currentScreen instanceof GuiJailControl) {
                        ((GuiJailControl) mc.currentScreen).update(msg.snapshot);
                    } else {
                        mc.displayGuiScreen(new GuiJailControl(msg.snapshot));
                    }
                });
            }
        }
    }
}
