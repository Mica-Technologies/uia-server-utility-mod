package com.micatechnologies.minecraft.sum.atm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for the {@link AtmPacketTransaction} wire format (two raw ints:
 * {@code action}, {@code amount}). The {@code Handler} is server/inventory-coupled and out of
 * unit scope; this locks that {@code toBytes}/{@code fromBytes} are symmetric and unclamped
 * (validation is deliberately server-side, not at the wire).
 */
class AtmPacketTransactionTest {

    private static AtmPacketTransaction roundTrip(int action, int amount) {
        ByteBuf buf = Unpooled.buffer();
        new AtmPacketTransaction(action, amount).toBytes(buf);
        AtmPacketTransaction out = new AtmPacketTransaction();
        out.fromBytes(buf);
        return out;
    }

    @Test
    void withdrawRoundTrips() {
        AtmPacketTransaction out = roundTrip(AtmPacketTransaction.ACTION_WITHDRAW_CASH, 50);
        assertEquals(AtmPacketTransaction.ACTION_WITHDRAW_CASH, out.getAction());
        assertEquals(50, out.getAmount());
    }

    @Test
    void depositAllRoundTrips() {
        AtmPacketTransaction out = roundTrip(AtmPacketTransaction.ACTION_DEPOSIT_CASH, 0);
        assertEquals(AtmPacketTransaction.ACTION_DEPOSIT_CASH, out.getAction());
        assertEquals(0, out.getAmount());
    }

    @Test
    void extremeAndNegativeAmountsSurviveUnclamped() {
        assertEquals(-1, roundTrip(0, -1).getAmount());
        assertEquals(Integer.MAX_VALUE, roundTrip(0, Integer.MAX_VALUE).getAmount());
        assertEquals(Integer.MIN_VALUE, roundTrip(0, Integer.MIN_VALUE).getAmount());
    }

    @Test
    void wireIsTwoIntsWide() {
        ByteBuf buf = Unpooled.buffer();
        new AtmPacketTransaction(1, 20).toBytes(buf);
        assertEquals(8, buf.readableBytes());
    }
}
