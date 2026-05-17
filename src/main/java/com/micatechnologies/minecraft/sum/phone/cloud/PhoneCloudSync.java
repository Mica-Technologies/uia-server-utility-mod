package com.micatechnologies.minecraft.sum.phone.cloud;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Server→client push of the calling player's complete {@link PhoneCloudData}. Sent in three
 * situations:
 *
 * <ol>
 *   <li>Reply to a {@link PhoneCloudFetchRequest} (phone GUI just opened).</li>
 *   <li>After the player's own action mutates their cloud (send message, add/remove contact,
 *       mark read), so the UI reflects the new state without an extra fetch round-trip.</li>
 *   <li>When this player is the recipient of a message and is online — server pushes so the
 *       new message appears immediately.</li>
 * </ol>
 *
 * <p>Sending the full cloud each time is simpler than delta packets and the cloud is small
 * (a few KB even with 50-message threads). The receiving GUI calls back into a static hook
 * on {@code GuiSumPhone} to merge.
 */
public class PhoneCloudSync implements IMessage {

    private NBTTagCompound payload;

    public PhoneCloudSync() {}

    public PhoneCloudSync(PhoneCloudData cloud) {
        this.payload = cloud.writeNbt();
    }

    public PhoneCloudData decode() {
        return PhoneCloudData.readNbt(payload);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.payload = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, payload);
    }

    public static class Handler implements IMessageHandler<PhoneCloudSync, IMessage> {

        @Override
        public IMessage onMessage(PhoneCloudSync msg, MessageContext ctx) {
            ClientDispatch.handle(msg);
            return null;
        }
    }

    /** Client-only inner class so loading {@link Handler} on a dedicated server doesn't drag
     *  in {@code net.minecraft.client.Minecraft}. */
    @SideOnly(Side.CLIENT)
    private static class ClientDispatch {
        static void handle(PhoneCloudSync msg) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                PhoneCloudData cloud = msg.decode();
                com.micatechnologies.minecraft.sum.phone.GuiSumPhone.receiveCloud(cloud);
            });
        }
    }
}
