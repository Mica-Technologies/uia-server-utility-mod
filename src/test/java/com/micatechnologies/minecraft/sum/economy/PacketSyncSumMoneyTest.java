package com.micatechnologies.minecraft.sum.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for {@link PacketSyncSumMoney} (a single {@code double} balance). The
 * client-side {@code Handler} is MC-coupled; this locks the wire format is symmetric and
 * carries the value bit-for-bit with no sanitization.
 */
class PacketSyncSumMoneyTest {

    private static double roundTrip(double balance) {
        ByteBuf buf = Unpooled.buffer();
        new PacketSyncSumMoney(balance).toBytes(buf);
        PacketSyncSumMoney out = new PacketSyncSumMoney();
        out.fromBytes(buf);
        return out.getBalance();
    }

    @Test
    void balancesRoundTrip() {
        assertEquals(0.0, roundTrip(0.0));
        assertEquals(1234.56, roundTrip(1234.56));
        assertEquals(-1.0, roundTrip(-1.0), "no clamp at the wire; server owns validation");
        assertEquals(Double.MAX_VALUE, roundTrip(Double.MAX_VALUE));
    }

    @Test
    void nonFiniteValuesSurviveBitForBit() {
        assertEquals(Double.NaN, roundTrip(Double.NaN));
        assertEquals(Double.POSITIVE_INFINITY, roundTrip(Double.POSITIVE_INFINITY));
    }

    @Test
    void wireIsOneDoubleWide() {
        ByteBuf buf = Unpooled.buffer();
        new PacketSyncSumMoney(42.0).toBytes(buf);
        assertEquals(8, buf.readableBytes());
    }
}
