package com.micatechnologies.minecraft.sum.jobs;

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
 * Server→client: open (or refresh) the job-board GUI with a snapshot of the current active
 * listings. Sent when a player right-clicks a job board, and re-sent to the acting player after
 * any job action so their GUI reflects the new state without reopening.
 *
 * <p>Mirrors {@code PacketOpenPlotBrowser}. Job listings are server-side {@link JobBoardSavedData}
 * and aren't otherwise synced to clients, so the snapshot is the only way the client sees them on
 * a dedicated server.
 */
public class PacketOpenJobBoard implements IMessage {

    private List<JobListing> listings = new ArrayList<>();

    public PacketOpenJobBoard() {}

    public PacketOpenJobBoard(List<JobListing> listings) {
        this.listings = listings;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        listings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            listings.add(JobListing.readFrom(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(listings.size());
        for (JobListing l : listings) {
            l.writeTo(buf);
        }
    }

    public static class Handler implements IMessageHandler<PacketOpenJobBoard, IMessage> {

        @Override
        public IMessage onMessage(PacketOpenJobBoard msg, MessageContext ctx) {
            ClientHandler.handle(msg);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static class ClientHandler {
            static void handle(PacketOpenJobBoard msg) {
                Minecraft mc = Minecraft.getMinecraft();
                mc.addScheduledTask(() -> {
                    net.minecraft.client.gui.GuiScreen current = mc.currentScreen;
                    if (current instanceof GuiJobBoard) {
                        ((GuiJobBoard) current).updateListings(msg.listings);
                    } else {
                        mc.displayGuiScreen(new GuiJobBoard(mc.player, msg.listings));
                    }
                });
            }
        }
    }
}
