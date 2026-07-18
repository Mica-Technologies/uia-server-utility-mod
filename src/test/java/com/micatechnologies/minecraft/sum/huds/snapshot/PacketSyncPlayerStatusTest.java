package com.micatechnologies.minecraft.sum.huds.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for {@link PacketSyncPlayerStatus} — the six packed fields of a
 * {@link PlayerStatusSnapshot}. The {@code Handler} bounces onto the client thread and is out of
 * scope; this locks that {@code toBytes}/{@code fromBytes} are symmetric (incl. UTF-8 plot names
 * and a negative next-milestone sentinel).
 */
class PacketSyncPlayerStatusTest {

    private static PlayerStatusSnapshot roundTrip(PlayerStatusSnapshot in) {
        ByteBuf buf = Unpooled.buffer();
        new PacketSyncPlayerStatus(in).toBytes(buf);
        PacketSyncPlayerStatus out = new PacketSyncPlayerStatus();
        out.fromBytes(buf);
        return out.snapshot;
    }

    @Test
    void populatedSnapshotRoundTrips() {
        PlayerStatusSnapshot r = roundTrip(
            new PlayerStatusSnapshot(9999L, "Downtown", "alex", 4, 14400, 36000));
        assertEquals(9999L, r.bankBalance);
        assertEquals("Downtown", r.plotName);
        assertEquals("alex", r.plotOwner);
        assertEquals(4, r.jobsAvailable);
        assertEquals(14400, r.loyaltyTicks);
        assertEquals(36000, r.nextMilestoneTicks);
    }

    @Test
    void emptySnapshotRoundTrips() {
        PlayerStatusSnapshot r = roundTrip(PlayerStatusSnapshot.empty());
        assertEquals("", r.plotName);
        assertEquals(0L, r.bankBalance);
        assertEquals(-1, r.nextMilestoneTicks);
    }

    @Test
    void unicodePlotNameSurvives() {
        assertEquals("Café №5",
            roundTrip(new PlayerStatusSnapshot(0L, "Café №5", "", 0, 0, -1)).plotName);
    }
}
