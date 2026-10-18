package com.micatechnologies.minecraft.sum.picture;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Server→client: open the picture frame editor. Sent only after the server has checked
 * {@link TileEntityPictureFrame#canEdit}, and carries the server's current settings plus its
 * limits, so the editor starts from the truth and can show what the server will accept.
 */
public class PacketOpenPictureFrame implements IMessage {

    private BlockPos pos;
    private PictureFrameSettings settings;
    private int maxSize;
    private int maxRenderDistance;

    public PacketOpenPictureFrame() {}

    public PacketOpenPictureFrame(BlockPos pos, PictureFrameSettings settings) {
        this.pos = pos;
        this.settings = settings;
        this.maxSize = com.micatechnologies.minecraft.sum.SumConfig.getPictureFrameMaxSize();
        this.maxRenderDistance = com.micatechnologies.minecraft.sum.SumConfig.getPictureFrameMaxRenderDistance();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        settings = PictureFrameSettings.readFrom(buf);
        maxSize = buf.readInt();
        maxRenderDistance = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        settings.writeTo(buf);
        buf.writeInt(maxSize);
        buf.writeInt(maxRenderDistance);
    }

    public static class Handler implements IMessageHandler<PacketOpenPictureFrame, IMessage> {

        @Override
        public IMessage onMessage(PacketOpenPictureFrame msg, MessageContext ctx) {
            // Registered Side.CLIENT — bounce through a client-only inner class so the server
            // classloader never resolves Minecraft when SumNetwork.init() runs.
            ClientHandler.handle(msg);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static class ClientHandler {
            static void handle(PacketOpenPictureFrame msg) {
                Minecraft mc = Minecraft.getMinecraft();
                mc.addScheduledTask(() -> mc.displayGuiScreen(new GuiPictureFrame(
                    msg.pos, msg.settings, msg.maxSize, msg.maxRenderDistance)));
            }
        }
    }
}
