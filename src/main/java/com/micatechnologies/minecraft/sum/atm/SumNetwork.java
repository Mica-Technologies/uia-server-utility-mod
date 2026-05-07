package com.micatechnologies.minecraft.sum.atm;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.economy.PacketSyncSumMoney;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * SUM's single {@link SimpleNetworkWrapper} channel and the discriminator IDs of every packet
 * it carries. {@link #init()} must be called from {@code Sum.preInit} before any packet is sent.
 */
public final class SumNetwork {

    public static final SimpleNetworkWrapper CHANNEL =
        NetworkRegistry.INSTANCE.newSimpleChannel(SumConstants.MOD_NAMESPACE);

    private static boolean initialized = false;

    private SumNetwork() {}

    public static void init() {
        if (initialized) {
            return;
        }
        CHANNEL.registerMessage(AtmPacketTransaction.Handler.class,
            AtmPacketTransaction.class, 0, Side.SERVER);
        CHANNEL.registerMessage(PacketSyncSumMoney.Handler.class,
            PacketSyncSumMoney.class, 1, Side.CLIENT);
        initialized = true;
    }
}
