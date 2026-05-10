package com.micatechnologies.minecraft.sum.atm;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.economy.PacketBillChangerAction;
import com.micatechnologies.minecraft.sum.economy.PacketSyncSumMoney;
import com.micatechnologies.minecraft.sum.jobs.PacketJobAction;
import com.micatechnologies.minecraft.sum.shop.PacketShopBuy;
import com.micatechnologies.minecraft.sum.shop.PacketShopOwnerAction;
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
        CHANNEL.registerMessage(PacketShopOwnerAction.Handler.class,
            PacketShopOwnerAction.class, 2, Side.SERVER);
        CHANNEL.registerMessage(PacketShopBuy.Handler.class,
            PacketShopBuy.class, 3, Side.SERVER);
        CHANNEL.registerMessage(PacketBillChangerAction.Handler.class,
            PacketBillChangerAction.class, 4, Side.SERVER);
        CHANNEL.registerMessage(PacketJobAction.Handler.class,
            PacketJobAction.class, 5, Side.SERVER);
        // Slot 6 is reserved (was PacketSignpostUpdate — moved to CSM,
        // see CSM CUSTOM_SIGNPOSTS_PLAN.md).
        initialized = true;
    }
}
