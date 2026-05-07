package com.micatechnologies.minecraft.sum.economy;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/**
 * SUM's per-player balance capability. Exposed via {@link CapabilitySumMoney#CAPABILITY} and
 * attached to every player on {@code AttachCapabilitiesEvent<Entity>}.
 *
 * <p>This is the SUM-native replacement for EconomyInc's {@code IMoney}. The
 * {@link EconomyBridge} facade prefers EconomyInc's capability when EconomyInc is loaded and
 * falls back to this one when it's absent, so individual SUM features (ATM, future bank teller,
 * postage, etc.) only ever talk to the bridge.
 */
public interface ISumMoney {

    double getBalance();

    /** Sets the balance directly. Negative values are clamped to 0. Server-only mutator;
     *  the client copy is updated via {@link #sync(EntityPlayer)} after server-side changes. */
    void setBalance(double balance);

    /** Adjusts the balance by {@code delta}. Refuses to overdraft (returns false without
     *  mutating). */
    boolean adjust(double delta);

    NBTTagCompound serializeNBT();

    void deserializeNBT(NBTTagCompound nbt);

    /** Pushes the current balance to the given player's client. No-op if called with a
     *  client-side player. Should be called after every server-side balance mutation so the
     *  client GUI sees the new value on the next frame. */
    void sync(EntityPlayer player);
}
