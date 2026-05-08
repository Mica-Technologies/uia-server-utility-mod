package com.micatechnologies.minecraft.sum.shop;

import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Buyer clicked Buy in {@link GuiShopBuyer}. The server is responsible for all validation. */
public class PacketShopBuy implements IMessage {

    private BlockPos pos;

    public PacketShopBuy() {}

    public PacketShopBuy(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
    }

    public static class Handler implements IMessageHandler<PacketShopBuy, IMessage> {

        @Override
        public IMessage onMessage(PacketShopBuy msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServer().addScheduledTask(() -> handle(player, msg));
            return null;
        }

        private void handle(EntityPlayerMP player, PacketShopBuy msg) {
            TileEntity te = player.world.getTileEntity(msg.pos);
            if (!(te instanceof TileEntityShop)) return;
            TileEntityShop shop = (TileEntityShop) te;
            // Reject if the buyer is too far from the shop (anti-cheat for spoofed packets).
            if (player.getDistanceSq(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            double balanceBefore = EconomyBridge.getBalance(player);
            TileEntityShop.BuyResult result = shop.attemptPurchase(player);
            player.sendMessage(TileEntityShop.describe(result, shop.getSaleCost(), balanceBefore));
        }
    }
}
