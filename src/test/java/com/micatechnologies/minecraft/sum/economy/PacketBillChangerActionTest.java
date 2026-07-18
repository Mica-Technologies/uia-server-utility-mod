package com.micatechnologies.minecraft.sum.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for {@link PacketBillChangerAction} (three raw ints for the {@link BlockPos}
 * plus an action int). Uses raw {@code writeInt} rather than {@code BlockPos.toLong} packing, so
 * far-out coordinates survive without the 26-bit-per-axis packing loss. The {@code Handler} is
 * server-coupled and out of scope.
 */
class PacketBillChangerActionTest {

    private static PacketBillChangerAction roundTrip(BlockPos pos, int action) {
        ByteBuf buf = Unpooled.buffer();
        new PacketBillChangerAction(pos, action).toBytes(buf);
        PacketBillChangerAction out = new PacketBillChangerAction();
        out.fromBytes(buf);
        return out;
    }

    @Test
    void bundleRoundTrips() {
        PacketBillChangerAction out = roundTrip(new BlockPos(10, 64, -30),
            PacketBillChangerAction.ACTION_BUNDLE);
        assertEquals(new BlockPos(10, 64, -30), out.getPos());
        assertEquals(PacketBillChangerAction.ACTION_BUNDLE, out.getAction());
    }

    @Test
    void unbundleRoundTrips() {
        PacketBillChangerAction out = roundTrip(new BlockPos(0, 0, 0),
            PacketBillChangerAction.ACTION_UNBUNDLE);
        assertEquals(PacketBillChangerAction.ACTION_UNBUNDLE, out.getAction());
    }

    @Test
    void farOutCoordinatesSurvive() {
        BlockPos far = new BlockPos(-30_000_000, 255, 29_999_999);
        assertEquals(far, roundTrip(far, PacketBillChangerAction.ACTION_BUNDLE).getPos());
    }

    @Test
    void toBytesRequiresAConstructedPos() {
        // The default ctor leaves pos null; toBytes dereferences it. Lock that contract.
        assertThrows(NullPointerException.class,
            () -> new PacketBillChangerAction().toBytes(Unpooled.buffer()));
    }

    @Test
    void wireIsFourIntsWide() {
        ByteBuf buf = Unpooled.buffer();
        new PacketBillChangerAction(new BlockPos(1, 2, 3), 0).toBytes(buf);
        assertEquals(16, buf.readableBytes());
    }
}
