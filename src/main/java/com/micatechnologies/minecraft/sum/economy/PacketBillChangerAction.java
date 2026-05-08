package com.micatechnologies.minecraft.sum.economy;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client → server packet for {@link BlockBillChanger} actions: bundle (bills → packet) or
 * unbundle (packet → bills). The server validates the player is near the changer and the
 * input slot has the right contents; the result chat-message comes from
 * {@link TileEntityBillChanger#describe}.
 */
public class PacketBillChangerAction implements IMessage {

    public static final int ACTION_BUNDLE = 0;
    public static final int ACTION_UNBUNDLE = 1;

    private BlockPos pos;
    private int action;

    public PacketBillChangerAction() {}

    public PacketBillChangerAction(BlockPos pos, int action) {
        this.pos = pos;
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.action = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
        buf.writeInt(action);
    }

    public static class Handler implements IMessageHandler<PacketBillChangerAction, IMessage> {

        @Override
        public IMessage onMessage(PacketBillChangerAction msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServer().addScheduledTask(() -> handle(player, msg));
            return null;
        }

        private void handle(EntityPlayerMP player, PacketBillChangerAction msg) {
            TileEntity te = player.world.getTileEntity(msg.pos);
            if (!(te instanceof TileEntityBillChanger)) return;
            if (player.getDistanceSq(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            TileEntityBillChanger changer = (TileEntityBillChanger) te;
            TileEntityBillChanger.ChangeResult result;
            switch (msg.action) {
                case ACTION_BUNDLE:
                    result = changer.bundle();
                    break;
                case ACTION_UNBUNDLE:
                    result = changer.unbundle();
                    break;
                default:
                    return;
            }
            if (result != TileEntityBillChanger.ChangeResult.OK) {
                player.sendMessage(TileEntityBillChanger.describe(result));
            }
            // Force a slot refresh so client sees the new input/output state immediately.
            player.openContainer.detectAndSendChanges();
        }
    }
}
