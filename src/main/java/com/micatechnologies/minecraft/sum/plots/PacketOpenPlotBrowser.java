package com.micatechnologies.minecraft.sum.plots;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Server→client: open (or refresh) the plot-browser GUI on the receiving player with a
 * snapshot of FOR_SALE plots in their current dimension. Triggered by
 * {@code /sum plots gui}, and re-sent after a successful buy so the GUI updates without
 * the player closing and reopening it.
 */
public class PacketOpenPlotBrowser implements IMessage {

    private List<PlotSnapshot> snapshots = new ArrayList<>();

    public PacketOpenPlotBrowser() {}

    public PacketOpenPlotBrowser(List<PlotSnapshot> snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        snapshots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            snapshots.add(PlotSnapshot.readFrom(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(snapshots.size());
        for (PlotSnapshot s : snapshots) {
            s.writeTo(buf);
        }
    }

    public static class Handler implements IMessageHandler<PacketOpenPlotBrowser, IMessage> {

        @Override
        public IMessage onMessage(PacketOpenPlotBrowser msg, MessageContext ctx) {
            // Registered Side.CLIENT; hop to a client-only inner class so the server
            // classloader never resolves Minecraft / GuiPlotBrowser.
            ClientHandler.handle(msg);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static class ClientHandler {
            static void handle(PacketOpenPlotBrowser msg) {
                Minecraft mc = Minecraft.getMinecraft();
                mc.addScheduledTask(() -> {
                    net.minecraft.client.gui.GuiScreen current = mc.currentScreen;
                    if (current instanceof GuiPlotBrowser) {
                        ((GuiPlotBrowser) current).updateSnapshots(msg.snapshots);
                    } else {
                        mc.displayGuiScreen(new GuiPlotBrowser(mc.player, msg.snapshots));
                    }
                });
            }
        }
    }
}
