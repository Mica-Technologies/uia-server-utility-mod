package com.micatechnologies.minecraft.sum.jail;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;

/**
 * What the Jail Control Tool's screen shows: every cell, who is in it, what is linked to it and
 * whether its alarm is sounding. Built on the server, since cells and inmates live only there.
 */
public final class JailControlSnapshot {

    public static final class InmateView {
        public final UUID playerId;
        public final String name;
        public final String jailedBy;
        /** -1 for open-ended. Relative to when the snapshot was built. */
        public final long remainingMillis;
        public final boolean online;

        InmateView(UUID playerId, String name, String jailedBy, long remainingMillis, boolean online) {
            this.playerId = playerId;
            this.name = name;
            this.jailedBy = jailedBy;
            this.remainingMillis = remainingMillis;
            this.online = online;
        }
    }

    public static final class CellView {
        public final String name;
        public final int dimension;
        public final BlockPos min;
        public final BlockPos max;
        public final int doors;
        public final int alarms;
        public final boolean sounding;
        public final List<InmateView> inmates;

        CellView(String name, int dimension, BlockPos min, BlockPos max, int doors, int alarms,
                 boolean sounding, List<InmateView> inmates) {
            this.name = name;
            this.dimension = dimension;
            this.min = min;
            this.max = max;
            this.doors = doors;
            this.alarms = alarms;
            this.sounding = sounding;
            this.inmates = inmates;
        }
    }

    public final List<CellView> cells;
    /** The cell the tool links doors and relays to, or "" for none. */
    public final String linkCell;

    JailControlSnapshot(List<CellView> cells, String linkCell) {
        this.cells = cells;
        this.linkCell = linkCell;
    }

    static JailControlSnapshot build(MinecraftServer server, String linkCell) {
        JailSavedData data = JailService.data(server);
        long now = System.currentTimeMillis();
        List<CellView> cells = new ArrayList<>();
        for (JailCell cell : data.getCells()) {
            List<InmateView> inmates = new ArrayList<>();
            for (Inmate inmate : data.getInmatesIn(cell.getName())) {
                inmates.add(new InmateView(inmate.getPlayerId(), inmate.getPlayerName(),
                    inmate.getJailedBy(), inmate.remainingMillis(now),
                    server.getPlayerList().getPlayerByUUID(inmate.getPlayerId()) != null));
            }
            World world = server.getWorld(cell.getDimensionId());
            cells.add(new CellView(cell.getName(), cell.getDimensionId(), cell.getMin(), cell.getMax(),
                cell.getDoors().size(), cell.getAlarms().size(),
                JailAlarms.isSounding(world, cell), inmates));
        }
        return new JailControlSnapshot(cells, data.getCell(linkCell) == null ? "" : linkCell);
    }

    void writeTo(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, linkCell);
        buf.writeInt(cells.size());
        for (CellView c : cells) {
            ByteBufUtils.writeUTF8String(buf, c.name);
            buf.writeInt(c.dimension);
            buf.writeLong(c.min.toLong());
            buf.writeLong(c.max.toLong());
            buf.writeInt(c.doors);
            buf.writeInt(c.alarms);
            buf.writeBoolean(c.sounding);
            buf.writeInt(c.inmates.size());
            for (InmateView i : c.inmates) {
                buf.writeLong(i.playerId.getMostSignificantBits());
                buf.writeLong(i.playerId.getLeastSignificantBits());
                ByteBufUtils.writeUTF8String(buf, i.name);
                ByteBufUtils.writeUTF8String(buf, i.jailedBy);
                buf.writeLong(i.remainingMillis);
                buf.writeBoolean(i.online);
            }
        }
    }

    static JailControlSnapshot readFrom(ByteBuf buf) {
        String linkCell = ByteBufUtils.readUTF8String(buf);
        int cellCount = buf.readInt();
        List<CellView> cells = new ArrayList<>(cellCount);
        for (int c = 0; c < cellCount; c++) {
            String name = ByteBufUtils.readUTF8String(buf);
            int dim = buf.readInt();
            BlockPos min = BlockPos.fromLong(buf.readLong());
            BlockPos max = BlockPos.fromLong(buf.readLong());
            int doors = buf.readInt();
            int alarms = buf.readInt();
            boolean sounding = buf.readBoolean();
            int inmateCount = buf.readInt();
            List<InmateView> inmates = new ArrayList<>(inmateCount);
            for (int i = 0; i < inmateCount; i++) {
                UUID id = new UUID(buf.readLong(), buf.readLong());
                String inmateName = ByteBufUtils.readUTF8String(buf);
                String by = ByteBufUtils.readUTF8String(buf);
                long remaining = buf.readLong();
                boolean online = buf.readBoolean();
                inmates.add(new InmateView(id, inmateName, by, remaining, online));
            }
            cells.add(new CellView(name, dim, min, max, doors, alarms, sounding, inmates));
        }
        return new JailControlSnapshot(cells, linkCell);
    }
}
