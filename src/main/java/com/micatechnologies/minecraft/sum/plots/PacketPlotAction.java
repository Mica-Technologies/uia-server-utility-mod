package com.micatechnologies.minecraft.sum.plots;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import com.micatechnologies.minecraft.sum.economy.WalletService;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client→server actions for the plot browser GUI. Currently just {@link #ACTION_BUY};
 * future actions (e.g. inspect, transfer-request) can reuse this packet with new
 * ACTION constants.
 */
public class PacketPlotAction implements IMessage {

    public static final int ACTION_BUY = 0;

    private int action;
    private UUID plotId;

    public PacketPlotAction() {}

    public PacketPlotAction(int action, UUID plotId) {
        this.action = action;
        this.plotId = plotId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readInt();
        this.plotId = new UUID(buf.readLong(), buf.readLong());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action);
        buf.writeLong(plotId.getMostSignificantBits());
        buf.writeLong(plotId.getLeastSignificantBits());
    }

    public static class Handler implements IMessageHandler<PacketPlotAction, IMessage> {

        @Override
        public IMessage onMessage(PacketPlotAction msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServer().addScheduledTask(() -> handle(player, msg));
            return null;
        }

        private void handle(EntityPlayerMP player, PacketPlotAction msg) {
            if (msg.action != ACTION_BUY) {
                Sum.LOGGER.warn("[plots] unknown action {} from {}", msg.action, player.getName());
                return;
            }
            SumPlotsWorldSavedData data = SumPlotsWorldSavedData.get(player.world);
            SumPlot plot = data.getById(msg.plotId);
            if (plot == null) {
                tell(player, TextFormatting.RED, "That plot no longer exists.");
                refresh(player, data);
                return;
            }
            if (plot.getDimensionId() != player.dimension) {
                tell(player, TextFormatting.RED,
                    "That plot is in a different dimension. Travel there to buy it.");
                refresh(player, data);
                return;
            }
            if (plot.getStatus() != PlotStatus.FOR_SALE) {
                tell(player, TextFormatting.RED,
                    "That plot isn't for sale (status: " + plot.getStatus() + ").");
                refresh(player, data);
                return;
            }
            if (plot.getPrice() <= 0) {
                tell(player, TextFormatting.RED,
                    "That plot has no listed price; ask an admin to set one.");
                refresh(player, data);
                return;
            }
            if (!WalletService.isAvailable()) {
                tell(player, TextFormatting.RED, "No economy backend is loaded.");
                return;
            }
            double balance = WalletService.getTotal(player);
            if (Double.isNaN(balance) || balance < plot.getPrice()) {
                tell(player, TextFormatting.RED, "Insufficient funds. Need $"
                    + String.format(Locale.ROOT, "%.2f", plot.getPrice())
                    + ", have $" + String.format(Locale.ROOT, "%.2f", balance) + ".");
                return;
            }
            if (!WalletService.spend(player, plot.getPrice())) {
                tell(player, TextFormatting.RED, "Charge failed; purchase aborted.");
                return;
            }
            plot.setOwner(player.getUniqueID(), player.getName());
            plot.setStatus(PlotStatus.OWNED);
            data.touch();
            tell(player, TextFormatting.GREEN, "Bought " + plot.getDisplayName()
                + " for $" + String.format(Locale.ROOT, "%.2f", plot.getPrice()) + ".");
            refresh(player, data);
        }

        /** Send the player an updated FOR_SALE snapshot for their current dimension so the
         *  GUI redraws without them closing it. */
        private void refresh(EntityPlayerMP player, SumPlotsWorldSavedData data) {
            List<PlotSnapshot> snapshots = new ArrayList<>();
            for (SumPlot p : data.getPlotsForSale()) {
                if (p.getDimensionId() == player.dimension) {
                    snapshots.add(PlotSnapshot.from(p));
                }
            }
            SumNetwork.CHANNEL.sendTo(new PacketOpenPlotBrowser(snapshots), player);
        }

        private void tell(EntityPlayerMP player, TextFormatting color, String msg) {
            player.sendMessage(new TextComponentString(color + msg));
        }
    }
}
