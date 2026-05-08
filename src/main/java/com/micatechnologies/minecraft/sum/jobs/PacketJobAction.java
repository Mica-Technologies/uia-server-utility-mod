package com.micatechnologies.minecraft.sum.jobs;

import com.micatechnologies.minecraft.sum.Sum;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client→server actions on a {@link JobListing}. Currently only "remove"; future actions
 * (claim/complete) can re-use this packet by adding new ACTION constants.
 */
public class PacketJobAction implements IMessage {

    public static final int ACTION_REMOVE = 0;

    private int action;
    private UUID listingId;

    public PacketJobAction() {}

    public PacketJobAction(int action, UUID listingId) {
        this.action = action;
        this.listingId = listingId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readInt();
        this.listingId = new UUID(buf.readLong(), buf.readLong());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action);
        buf.writeLong(listingId.getMostSignificantBits());
        buf.writeLong(listingId.getLeastSignificantBits());
    }

    public static class Handler implements IMessageHandler<PacketJobAction, IMessage> {

        @Override
        public IMessage onMessage(PacketJobAction msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServer().addScheduledTask(() -> handle(player, msg));
            return null;
        }

        private void handle(EntityPlayerMP player, PacketJobAction msg) {
            JobBoardSavedData data = JobBoardSavedData.get(player.world);
            if (msg.action != ACTION_REMOVE) {
                Sum.LOGGER.warn("[jobs] unknown action {} from {}", msg.action, player.getName());
                return;
            }
            // Look up the listing first so we can verify ownership before mutating.
            for (JobListing l : data.getActive(System.currentTimeMillis())) {
                if (l.id.equals(msg.listingId)) {
                    if (!l.posterUuid.equals(player.getUniqueID())
                        && !player.canUseCommand(2, "sum.jobs.remove_any")) {
                        player.sendMessage(new TextComponentString(TextFormatting.RED
                            + "You can only remove your own listings."));
                        return;
                    }
                    if (data.removeListing(msg.listingId)) {
                        player.sendMessage(new TextComponentString(TextFormatting.GREEN
                            + "Listing removed."));
                    }
                    return;
                }
            }
        }
    }
}
