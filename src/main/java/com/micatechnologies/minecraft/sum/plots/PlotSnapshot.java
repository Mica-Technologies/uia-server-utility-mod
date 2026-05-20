package com.micatechnologies.minecraft.sum.plots;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;

/**
 * Client-visible projection of a {@link SumPlot} for the plot-browser GUI. Plots live in
 * server-only saved data ({@link SumPlotsWorldSavedData}); the client can't read them
 * directly on a dedicated server, so the server packs a snapshot list into
 * {@code PacketOpenPlotBrowser} and sends it on demand. This class is the per-plot row
 * in that list.
 */
public class PlotSnapshot {

    public final UUID plotId;
    public final String displayName;
    public final String ownerName;
    public final double price;
    public final int dimensionId;
    public final BlockPos cornerA;
    public final BlockPos cornerB;
    public final long volume;

    public PlotSnapshot(UUID plotId, String displayName, String ownerName, double price,
                        int dimensionId, BlockPos cornerA, BlockPos cornerB, long volume) {
        this.plotId = plotId;
        this.displayName = displayName;
        this.ownerName = ownerName == null ? "" : ownerName;
        this.price = price;
        this.dimensionId = dimensionId;
        this.cornerA = cornerA;
        this.cornerB = cornerB;
        this.volume = volume;
    }

    public static PlotSnapshot from(SumPlot plot) {
        return new PlotSnapshot(plot.getPlotId(), plot.getDisplayName(), plot.getOwnerName(),
            plot.getPrice(), plot.getDimensionId(), plot.getCornerA(), plot.getCornerB(),
            plot.volume());
    }

    public BlockPos center() {
        return new BlockPos(
            (cornerA.getX() + cornerB.getX()) / 2,
            (cornerA.getY() + cornerB.getY()) / 2,
            (cornerA.getZ() + cornerB.getZ()) / 2);
    }

    public void writeTo(ByteBuf buf) {
        buf.writeLong(plotId.getMostSignificantBits());
        buf.writeLong(plotId.getLeastSignificantBits());
        writeString(buf, displayName);
        writeString(buf, ownerName);
        buf.writeDouble(price);
        buf.writeInt(dimensionId);
        buf.writeLong(cornerA.toLong());
        buf.writeLong(cornerB.toLong());
        buf.writeLong(volume);
    }

    public static PlotSnapshot readFrom(ByteBuf buf) {
        UUID id = new UUID(buf.readLong(), buf.readLong());
        String name = readString(buf);
        String owner = readString(buf);
        double price = buf.readDouble();
        int dim = buf.readInt();
        BlockPos a = BlockPos.fromLong(buf.readLong());
        BlockPos b = BlockPos.fromLong(buf.readLong());
        long vol = buf.readLong();
        return new PlotSnapshot(id, name, owner, price, dim, a, b, vol);
    }

    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readShort() & 0xFFFF;
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
