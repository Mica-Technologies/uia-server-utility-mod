package com.micatechnologies.minecraft.sum.serverconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for the versioned {@link ServerConfigSnapshot} wire format: ~17 scalar fields
 * plus six UTF-8 string lists. Locks the write/read symmetry (a field-order or type drift breaks
 * the config viewer), the version header position, null-list-element coalescing, and the
 * run-out-of-bytes contract the packet handler relies on.
 */
class ServerConfigSnapshotTest {

    private static ServerConfigSnapshot populated() {
        ServerConfigSnapshot s = new ServerConfigSnapshot();
        s.sleepVoteEnabled = true;
        s.sleepVoteThreshold = 55;
        s.beachesEnabled = true;
        s.beachesAnimated = false;
        s.beachesInfiniteBucket = true;
        s.beachesRealistic = false;
        s.beachesAffectedBlocks = Arrays.asList("minecraft:sand", "minecraft:gravel");
        s.pauserEnabled = true;
        s.movementToleranceEnabled = true;
        s.movementToleranceMultiplier = 1.75;
        s.loyaltyEnabled = true;
        s.loyaltyMilestones = Arrays.asList("60=money:100");
        s.loyaltySessionMilestones = Arrays.asList("30=command:say hi");
        s.autoDropperEnabled = true;
        s.autoDropperTickInterval = 40;
        s.borderEnabled = true;
        s.borderEntries = Arrays.asList("0=1000:bounce", "-1=200:loop");
        s.roamerWalkableBlocks = Arrays.asList("minecraft:grass_path");
        s.roadrunnerSpeedBlocks = Arrays.asList("minecraft:concrete=1.80");
        return s;
    }

    private static ServerConfigSnapshot roundTrip(ServerConfigSnapshot s) {
        ByteBuf buf = Unpooled.buffer();
        s.writeTo(buf);
        return ServerConfigSnapshot.readFrom(buf);
    }

    @Test
    void fullRoundTripPreservesEveryField() {
        ServerConfigSnapshot r = roundTrip(populated());
        assertEquals(true, r.sleepVoteEnabled);
        assertEquals(55, r.sleepVoteThreshold);
        assertEquals(true, r.beachesEnabled);
        assertEquals(false, r.beachesAnimated);
        assertEquals(true, r.beachesInfiniteBucket);
        assertEquals(false, r.beachesRealistic);
        assertEquals(Arrays.asList("minecraft:sand", "minecraft:gravel"), r.beachesAffectedBlocks);
        assertEquals(true, r.pauserEnabled);
        assertEquals(true, r.movementToleranceEnabled);
        assertEquals(1.75, r.movementToleranceMultiplier, 1e-9);
        assertEquals(true, r.loyaltyEnabled);
        assertEquals(Arrays.asList("60=money:100"), r.loyaltyMilestones);
        assertEquals(Arrays.asList("30=command:say hi"), r.loyaltySessionMilestones);
        assertEquals(true, r.autoDropperEnabled);
        assertEquals(40, r.autoDropperTickInterval);
        assertEquals(true, r.borderEnabled);
        assertEquals(Arrays.asList("0=1000:bounce", "-1=200:loop"), r.borderEntries);
        assertEquals(Arrays.asList("minecraft:grass_path"), r.roamerWalkableBlocks);
        assertEquals(Arrays.asList("minecraft:concrete=1.80"), r.roadrunnerSpeedBlocks);
    }

    @Test
    void emptySnapshotRoundTripsToEmptyLists() {
        ServerConfigSnapshot r = roundTrip(new ServerConfigSnapshot());
        assertTrue(r.beachesAffectedBlocks.isEmpty());
        assertTrue(r.borderEntries.isEmpty());
        assertTrue(r.roadrunnerSpeedBlocks.isEmpty());
        assertEquals(0, r.sleepVoteThreshold);
    }

    @Test
    void multibyteStringsSurvive() {
        ServerConfigSnapshot s = new ServerConfigSnapshot();
        s.beachesAffectedBlocks = Arrays.asList("minecraft:café", "ρ=σ");
        assertEquals(Arrays.asList("minecraft:café", "ρ=σ"), roundTrip(s).beachesAffectedBlocks);
    }

    @Test
    void versionIsTheFirstIntOnTheWire() {
        ByteBuf buf = Unpooled.buffer();
        new ServerConfigSnapshot().writeTo(buf);
        assertEquals(ServerConfigSnapshot.VERSION, buf.readInt());
    }

    @Test
    void nullListElementReadsBackAsEmptyString() {
        ServerConfigSnapshot s = new ServerConfigSnapshot();
        s.borderEntries = Arrays.asList("a", null, "b");
        assertEquals(Arrays.asList("a", "", "b"), roundTrip(s).borderEntries);
    }

    @Test
    void truncatedBufferThrows() {
        // Only the version int is present; readFrom runs out of bytes at the first field. The
        // packet handler catches this and discards — pin that a short buffer does throw.
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(ServerConfigSnapshot.VERSION);
        assertThrows(IndexOutOfBoundsException.class, () -> ServerConfigSnapshot.readFrom(buf));
    }

    @Test
    void unmodifiableDefaultListsStillSerialize() {
        // The default lists are Collections.emptyList(); make sure writing them is fine.
        ServerConfigSnapshot s = new ServerConfigSnapshot();
        s.loyaltyMilestones = Collections.emptyList();
        assertTrue(roundTrip(s).loyaltyMilestones.isEmpty());
    }
}
