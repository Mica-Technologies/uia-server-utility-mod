package com.micatechnologies.minecraft.sum.signpost;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client→Server packet: replace the entire arm list of the signpost at {@code pos} with the
 * supplied arms. Server validates that the player is within reach (8 blocks) of the targeted
 * signpost before applying.
 */
public class PacketSignpostUpdate implements IMessage {

    private static final int MAX_LABEL_BYTES = 64;
    private static final double MAX_REACH_SQ = 8.0 * 8.0;

    private BlockPos pos;
    private List<SignpostArm> arms = new ArrayList<>();

    public PacketSignpostUpdate() {}

    public PacketSignpostUpdate(BlockPos pos, List<SignpostArm> arms) {
        this.pos = pos;
        this.arms = new ArrayList<>(arms);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        int count = buf.readByte();
        if (count < 0 || count > TileEntitySignpost.MAX_ARMS) {
            return;
        }
        for (int i = 0; i < count; i++) {
            float angle = buf.readFloat();
            int len = buf.readShort();
            if (len < 0 || len > MAX_LABEL_BYTES) {
                return;
            }
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            arms.add(new SignpostArm(new String(bytes, StandardCharsets.UTF_8), angle));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        int count = Math.min(arms.size(), TileEntitySignpost.MAX_ARMS);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            SignpostArm arm = arms.get(i);
            buf.writeFloat(arm.getAngleDegrees());
            byte[] bytes = arm.getLabel().getBytes(StandardCharsets.UTF_8);
            int len = Math.min(bytes.length, MAX_LABEL_BYTES);
            buf.writeShort(len);
            buf.writeBytes(bytes, 0, len);
        }
    }

    public static class Handler implements IMessageHandler<PacketSignpostUpdate, IMessage> {
        @Override
        public IMessage onMessage(PacketSignpostUpdate msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            WorldServer world = (WorldServer) player.world;
            world.addScheduledTask(() -> handle(player, msg));
            return null;
        }

        private static void handle(EntityPlayerMP player, PacketSignpostUpdate msg) {
            if (msg.pos == null) {
                return;
            }
            if (player.getDistanceSqToCenter(msg.pos) > MAX_REACH_SQ) {
                return;
            }
            TileEntity te = player.world.getTileEntity(msg.pos);
            if (!(te instanceof TileEntitySignpost)) {
                return;
            }
            ((TileEntitySignpost) te).replaceArms(msg.arms);
        }
    }
}
