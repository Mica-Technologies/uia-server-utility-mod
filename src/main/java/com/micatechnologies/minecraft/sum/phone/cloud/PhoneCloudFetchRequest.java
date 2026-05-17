package com.micatechnologies.minecraft.sum.phone.cloud;

import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client→server empty-body request asking the server to push a {@link PhoneCloudSync} for
 * the calling player. Sent every time the phone GUI opens so the client starts with a fresh
 * snapshot (the cloud may have changed since the player last looked).
 */
public class PhoneCloudFetchRequest implements IMessage {

    public PhoneCloudFetchRequest() {}

    @Override public void fromBytes(ByteBuf buf) {}
    @Override public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PhoneCloudFetchRequest, IMessage> {

        @Override
        public IMessage onMessage(PhoneCloudFetchRequest msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServer().addScheduledTask(() -> {
                PhoneCloudSavedData data = PhoneCloudSavedData.get(player.world);
                PhoneCloudData cloud = data.getOrCreateForPlayer(player);
                data.refreshContactNumbers(cloud);
                SumNetwork.CHANNEL.sendTo(new PhoneCloudSync(cloud), player);
            });
            return null;
        }
    }
}
