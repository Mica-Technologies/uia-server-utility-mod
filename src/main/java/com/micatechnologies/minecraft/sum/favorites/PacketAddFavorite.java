package com.micatechnologies.minecraft.sum.favorites;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server → client message asking the receiving client to add an item to its local
 * {@link FavoritesStore}. Sent when the server binds a personal item to a player (phone /
 * debit card) so the player's favorites tab always reflects what's bound to them, even when
 * they log into a different client. {@link FavoritesStore#add} is idempotent, so a repeat
 * packet is harmless.
 */
public class PacketAddFavorite implements IMessage {

    private String key;

    public PacketAddFavorite() {}

    public PacketAddFavorite(FavoriteKey key) {
        this.key = key.toString();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        // String#toString from FavoriteKey is "registryname#meta"; well under the 32767 cap.
        this.key = new PacketBuffer(buf).readString(256);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        new PacketBuffer(buf).writeString(this.key == null ? "" : this.key);
    }

    public static class Handler implements IMessageHandler<PacketAddFavorite, IMessage> {

        @Override
        public IMessage onMessage(PacketAddFavorite msg, MessageContext ctx) {
            // Marshal back onto the client main thread before mutating the favorites store
            // and writing to disk — FavoritesStore is internally synchronized, but the save()
            // does file I/O that we'd rather not run on the netty thread.
            Minecraft.getMinecraft().addScheduledTask(() -> {
                FavoriteKey key = FavoriteKey.parse(msg.key);
                if (key == null) {
                    return;
                }
                if (FavoritesStore.add(key)) {
                    FavoritesStore.save();
                }
            });
            return null;
        }
    }
}
