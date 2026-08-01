package com.micatechnologies.minecraft.sum.atm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for the {@link AtmPacketTransaction} wire format (an int {@code action} and a
 * double {@code amount} in dollars). The {@code Handler} is server/inventory-coupled and out of
 * unit scope; this locks that {@code toBytes}/{@code fromBytes} are symmetric and unclamped
 * (validation is deliberately server-side, not at the wire).
 */
class AtmPacketTransactionTest {

    private static AtmPacketTransaction roundTrip(int action, double amount) {
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
        assertEquals(50.0, out.getAmount());
    }

    @Test
    void depositAllRoundTrips() {
        AtmPacketTransaction out = roundTrip(AtmPacketTransaction.ACTION_DEPOSIT_CASH, 0);
        assertEquals(AtmPacketTransaction.ACTION_DEPOSIT_CASH, out.getAction());
        assertEquals(0.0, out.getAmount());
    }

    @Test
    void fractionalAmountsSurvive() {
        // Wallet transfers carry cents; only cash is constrained to whole dollars, server-side.
        assertEquals(12.34, roundTrip(AtmPacketTransaction.ACTION_WITHDRAW_TO_WALLET, 12.34).getAmount());
        assertEquals(0.01, roundTrip(AtmPacketTransaction.ACTION_DEPOSIT_WALLET, 0.01).getAmount());
    }

    @Test
    void extremeAndNegativeAmountsSurviveUnclamped() {
        assertEquals(-1.0, roundTrip(0, -1.0).getAmount());
        assertEquals(Double.MAX_VALUE, roundTrip(0, Double.MAX_VALUE).getAmount());
        assertEquals(-Double.MAX_VALUE, roundTrip(0, -Double.MAX_VALUE).getAmount());
    }

    @Test
    void wireIsAnIntPlusADouble() {
        ByteBuf buf = Unpooled.buffer();
        new AtmPacketTransaction(1, 20.0).toBytes(buf);
        assertEquals(12, buf.readableBytes());
    }
}
