package com.micatechnologies.minecraft.sum.shop;

import com.micatechnologies.minecraft.sum.Sum;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Owner-only client → server actions for {@link TileEntityShop}: change sale amount/cost,
 * withdraw funds, toggle infinite stock (admin only). The server validates the player owns
 * the shop before applying any action.
 */
public class PacketShopOwnerAction implements IMessage {

    public static final int ACTION_SET_AMOUNT = 0;
    public static final int ACTION_SET_COST = 1;
    public static final int ACTION_WITHDRAW = 2;
    public static final int ACTION_TOGGLE_INFINITE = 3;

    private BlockPos pos;
    private int action;
    private double value;

    public PacketShopOwnerAction() {}

    public PacketShopOwnerAction(BlockPos pos, int action, double value) {
        this.pos = pos;
        this.action = action;
        this.value = value;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.action = buf.readInt();
        this.value = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
        buf.writeInt(action);
        buf.writeDouble(value);
    }

    public static class Handler implements IMessageHandler<PacketShopOwnerAction, IMessage> {

        @Override
        public IMessage onMessage(PacketShopOwnerAction msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServer().addScheduledTask(() -> handle(player, msg));
            return null;
        }

        private void handle(EntityPlayerMP player, PacketShopOwnerAction msg) {
            TileEntity te = player.world.getTileEntity(msg.pos);
            if (!(te instanceof TileEntityShop)) return;
            TileEntityShop shop = (TileEntityShop) te;
            if (!shop.isOwner(player)) {
                Sum.LOGGER.warn("[shop] non-owner {} tried to mutate shop at {}",
                    player.getName(), msg.pos);
                return;
            }
            switch (msg.action) {
                case ACTION_SET_AMOUNT:
                    shop.setSaleAmount((int) msg.value);
                    break;
                case ACTION_SET_COST:
                    shop.setSaleCost(msg.value);
                    break;
                case ACTION_WITHDRAW: {
                    double withdrew = shop.withdrawFunds(player);
                    if (withdrew > 0) {
                        player.sendMessage(new TextComponentString(TextFormatting.GREEN
                            + "Withdrew $" + String.format(Locale.ROOT, "%.2f", withdrew)
                            + " from shop."));
                    } else {
                        player.sendMessage(new TextComponentString(TextFormatting.YELLOW
                            + "Nothing to withdraw."));
                    }
                    break;
                }
                case ACTION_TOGGLE_INFINITE:
                    if (!player.canUseCommand(2, "sum.shop.infinite")) {
                        player.sendMessage(new TextComponentString(TextFormatting.RED
                            + "Infinite stock is admin-only."));
                        return;
                    }
                    shop.setInfiniteStock(!shop.isInfiniteStock());
                    player.sendMessage(new TextComponentString(TextFormatting.GREEN
                        + "Infinite stock " + (shop.isInfiniteStock() ? "enabled" : "disabled")
                        + "."));
                    break;
                default:
                    Sum.LOGGER.warn("[shop] unknown owner action {} from {}",
                        msg.action, player.getName());
                    break;
            }
        }
    }
}
