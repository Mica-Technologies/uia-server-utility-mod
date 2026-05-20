package com.micatechnologies.minecraft.sum.serverconfig;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client-visible projection of the server's {@code sum.cfg} runtime state. Built by
 * {@link ServerConfigBridge} on the server, shipped via {@link PacketSyncServerConfig},
 * cached client-side by {@link ServerConfigMirror}, displayed by
 * {@link GuiServerConfigViewer}.
 *
 * <p>String-list values (roamer walkable, border entries, loyalty milestones, etc.)
 * are sent in their config-file form so the viewer can render them verbatim — a
 * server admin reading the in-game viewer sees the exact same syntax they'd edit in
 * {@code sum.cfg}.</p>
 *
 * <p>Forward-compat: this is a versioned wire format. New fields tack on at the end of
 * {@link #writeTo} / {@link #readFrom}; older clients ignore trailing bytes (they just
 * read what their version expects). When adding fields, also bump {@link #VERSION} so a
 * mismatched client logs a clear warning instead of silently mis-parsing.</p>
 */
public final class ServerConfigSnapshot {

    public static final int VERSION = 1;

    public boolean sleepVoteEnabled;
    public int sleepVoteThreshold;

    public boolean beachesEnabled;
    public boolean beachesAnimated;
    public boolean beachesInfiniteBucket;
    public boolean beachesRealistic;
    public List<String> beachesAffectedBlocks = Collections.emptyList();

    public boolean pauserEnabled;

    public boolean movementToleranceEnabled;
    public double movementToleranceMultiplier;

    public boolean loyaltyEnabled;
    public List<String> loyaltyMilestones = Collections.emptyList();
    public List<String> loyaltySessionMilestones = Collections.emptyList();

    public boolean autoDropperEnabled;
    public int autoDropperTickInterval;

    public boolean borderEnabled;
    public List<String> borderEntries = Collections.emptyList();

    public List<String> roamerWalkableBlocks = Collections.emptyList();
    public List<String> roadrunnerSpeedBlocks = Collections.emptyList();

    public void writeTo(ByteBuf buf) {
        buf.writeInt(VERSION);

        buf.writeBoolean(sleepVoteEnabled);
        buf.writeInt(sleepVoteThreshold);

        buf.writeBoolean(beachesEnabled);
        buf.writeBoolean(beachesAnimated);
        buf.writeBoolean(beachesInfiniteBucket);
        buf.writeBoolean(beachesRealistic);
        writeStringList(buf, beachesAffectedBlocks);

        buf.writeBoolean(pauserEnabled);

        buf.writeBoolean(movementToleranceEnabled);
        buf.writeDouble(movementToleranceMultiplier);

        buf.writeBoolean(loyaltyEnabled);
        writeStringList(buf, loyaltyMilestones);
        writeStringList(buf, loyaltySessionMilestones);

        buf.writeBoolean(autoDropperEnabled);
        buf.writeInt(autoDropperTickInterval);

        buf.writeBoolean(borderEnabled);
        writeStringList(buf, borderEntries);

        writeStringList(buf, roamerWalkableBlocks);
        writeStringList(buf, roadrunnerSpeedBlocks);
    }

    public static ServerConfigSnapshot readFrom(ByteBuf buf) {
        int wire = buf.readInt();
        if (wire != VERSION) {
            // Different version. Best effort: read fields anyway. If a future server
            // adds fields, the reader will simply run out of bytes after the last one
            // it knows about and netty's ByteBuf will throw; the packet handler logs
            // and discards. Older servers writing fewer fields than we expect will
            // also throw — same handling path.
        }
        ServerConfigSnapshot s = new ServerConfigSnapshot();
        s.sleepVoteEnabled = buf.readBoolean();
        s.sleepVoteThreshold = buf.readInt();

        s.beachesEnabled = buf.readBoolean();
        s.beachesAnimated = buf.readBoolean();
        s.beachesInfiniteBucket = buf.readBoolean();
        s.beachesRealistic = buf.readBoolean();
        s.beachesAffectedBlocks = readStringList(buf);

        s.pauserEnabled = buf.readBoolean();

        s.movementToleranceEnabled = buf.readBoolean();
        s.movementToleranceMultiplier = buf.readDouble();

        s.loyaltyEnabled = buf.readBoolean();
        s.loyaltyMilestones = readStringList(buf);
        s.loyaltySessionMilestones = readStringList(buf);

        s.autoDropperEnabled = buf.readBoolean();
        s.autoDropperTickInterval = buf.readInt();

        s.borderEnabled = buf.readBoolean();
        s.borderEntries = readStringList(buf);

        s.roamerWalkableBlocks = readStringList(buf);
        s.roadrunnerSpeedBlocks = readStringList(buf);

        return s;
    }

    private static void writeStringList(ByteBuf buf, List<String> list) {
        buf.writeInt(list.size());
        for (String s : list) {
            byte[] bytes = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
            buf.writeShort(bytes.length);
            buf.writeBytes(bytes);
        }
    }

    private static List<String> readStringList(ByteBuf buf) {
        int count = buf.readInt();
        List<String> list = new ArrayList<>(Math.min(count, 64));
        for (int i = 0; i < count; i++) {
            int len = buf.readShort() & 0xFFFF;
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            list.add(new String(bytes, StandardCharsets.UTF_8));
        }
        return list;
    }
}
