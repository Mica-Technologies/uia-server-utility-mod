package com.micatechnologies.minecraft.sum.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

/**
 * Tests the pure balance model of {@link DefaultSumMoney} — the only concrete {@link ISumMoney}.
 * Covers the negative-clamp in {@code setBalance}, the overdraft guard in {@code adjust} (which
 * must NOT mutate on a rejected withdrawal), and the {@code "balance"} NBT round-trip. The
 * {@code sync()} path is client/network-coupled and out of unit scope.
 */
class DefaultSumMoneyTest {

    private static final double EPS = 1e-9;

    @Test
    void setBalanceClampsNegativesToZero() {
        DefaultSumMoney money = new DefaultSumMoney();
        money.setBalance(-5.0);
        assertEquals(0.0, money.getBalance(), EPS);
        money.setBalance(100.0);
        assertEquals(100.0, money.getBalance(), EPS);
        money.setBalance(0.0);
        assertEquals(0.0, money.getBalance(), EPS);
    }

    @Test
    void adjustAppliesWhenSolvent() {
        DefaultSumMoney money = new DefaultSumMoney();
        assertTrue(money.adjust(50.0));
        assertEquals(50.0, money.getBalance(), EPS);
        assertTrue(money.adjust(-50.0));
        assertEquals(0.0, money.getBalance(), EPS);
    }

    @Test
    void adjustRejectsOverdraftWithoutMutating() {
        DefaultSumMoney money = new DefaultSumMoney();
        assertFalse(money.adjust(-1.0));
        assertEquals(0.0, money.getBalance(), EPS, "balance must be unchanged after a rejected adjust");
    }

    @Test
    void adjustRejectsSubCentOverdraft() {
        // next = -0.005 < 0 → rejected. Guards the boundary just below zero.
        DefaultSumMoney money = new DefaultSumMoney();
        assertFalse(money.adjust(-0.005));
        assertEquals(0.0, money.getBalance(), EPS);
    }

    @Test
    void nbtRoundTripPreservesBalance() {
        DefaultSumMoney money = new DefaultSumMoney();
        money.setBalance(1234.56);
        NBTTagCompound nbt = money.serializeNBT();

        DefaultSumMoney restored = new DefaultSumMoney();
        restored.deserializeNBT(nbt);
        assertEquals(1234.56, restored.getBalance(), EPS);
    }

    @Test
    void deserializeMissingKeyYieldsZero() {
        DefaultSumMoney money = new DefaultSumMoney();
        money.setBalance(500.0);
        money.deserializeNBT(new NBTTagCompound());
        assertEquals(0.0, money.getBalance(), EPS);
    }

    @Test
    void setBalanceHasNoNaNGuard() {
        // Documents current behavior: Math.max(0.0, NaN) == NaN, so setBalance does not sanitize
        // NaN the way MoneyTransfer does. Locking this so a future NaN guard is a conscious change.
        DefaultSumMoney money = new DefaultSumMoney();
        money.setBalance(Double.NaN);
        assertTrue(Double.isNaN(money.getBalance()));
    }
}
