package com.micatechnologies.minecraft.sum.pocket;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.atm.SumGuiHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client → server request to open the pocket-management GUI. Triggered by the pocket
 * keybind on the client. Server then calls {@link net.minecraft.entity.player.EntityPlayer
 * #openGui} which builds a {@link ContainerPocket} server-side and ships the standard
 * OPEN_WINDOW packet back, so the actual GUI display follows the vanilla
 * container-syncing path (slot contents stay in lockstep with server state automatically).
 *
 * <p>No payload — the player's identity comes from the packet context.
 */
public class PacketOpenPocketGui implements IMessage {

    public PacketOpenPocketGui() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketOpenPocketGui, IMessage> {

        @Override
        public IMessage onMessage(PacketOpenPocketGui msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            // Bounce onto the server thread before openGui — that path mutates
            // currentWindowId/openContainer which can't safely be touched off-thread.
            player.getServerWorld().addScheduledTask(() ->
                player.openGui(Sum.instance, SumGuiHandler.GUI_POCKET,
                    player.world, (int) player.posX, (int) player.posY, (int) player.posZ));
            return null;
        }
    }
}
