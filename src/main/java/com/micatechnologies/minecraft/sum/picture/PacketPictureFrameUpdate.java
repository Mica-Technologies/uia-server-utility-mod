package com.micatechnologies.minecraft.sum.picture;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client→server: the editor's Done button. The server re-checks everything the client could lie
 * about: that the player is near the frame and may edit it, that the URL passes
 * {@link PictureUrls#validate}, and that every number is inside the configured limits.
 *
 * <p>Every accepted URL change is logged with the player's name, so an operator can find out who
 * put a picture up.
 */
public class PacketPictureFrameUpdate implements IMessage {

    /** Same reach as opening a container. */
    private static final double MAX_DISTANCE_SQ = 64.0;

    private BlockPos pos;
    private PictureFrameSettings settings;

    public PacketPictureFrameUpdate() {}

    public PacketPictureFrameUpdate(BlockPos pos, PictureFrameSettings settings) {
        this.pos = pos;
        this.settings = settings;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        settings = PictureFrameSettings.readFrom(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        settings.writeTo(buf);
    }

    public static class Handler implements IMessageHandler<PacketPictureFrameUpdate, IMessage> {

        @Override
        public IMessage onMessage(PacketPictureFrameUpdate msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServer().addScheduledTask(() -> handle(player, msg));
            return null;
        }

        private static void handle(EntityPlayerMP player, PacketPictureFrameUpdate msg) {
            if (!player.world.isBlockLoaded(msg.pos)
                || player.getDistanceSqToCenter(msg.pos) > MAX_DISTANCE_SQ) {
                return;
            }
            TileEntity te = player.world.getTileEntity(msg.pos);
            if (!(te instanceof TileEntityPictureFrame)) return;
            TileEntityPictureFrame frame = (TileEntityPictureFrame) te;
            if (!frame.canEdit(player)) {
                Sum.LOGGER.warn("[picture-frame] {} tried to edit a frame they don't own at {}",
                    player.getName(), msg.pos);
                return;
            }
            String problem = PictureUrls.validate(msg.settings.getUrl(),
                SumConfig.getPictureFrameAllowedHosts());
            if (problem != null) {
                TextComponentString tcs = new TextComponentString(problem);
                tcs.getStyle().setColor(TextFormatting.RED);
                player.sendMessage(tcs);
                return;
            }
            PictureFrameSettings clamped = msg.settings.clamp(SumConfig.getPictureFrameMaxSize(),
                SumConfig.getPictureFrameMaxRenderDistance());
            String previousUrl = frame.getSettings().getUrl();
            frame.setSettings(clamped);
            if (!previousUrl.equals(clamped.getUrl())) {
                Sum.LOGGER.info("[picture-frame] {} set the frame at {} (dim {}) to '{}'",
                    player.getName(), msg.pos, player.dimension, clamped.getUrl());
            }
        }
    }
}
