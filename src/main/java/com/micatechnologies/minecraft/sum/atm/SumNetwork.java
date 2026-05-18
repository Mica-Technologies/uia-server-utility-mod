package com.micatechnologies.minecraft.sum.atm;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.economy.PacketBillChangerAction;
import com.micatechnologies.minecraft.sum.economy.PacketSyncSumMoney;
import com.micatechnologies.minecraft.sum.favorites.PacketAddFavorite;
import com.micatechnologies.minecraft.sum.jobs.PacketJobAction;
import com.micatechnologies.minecraft.sum.phone.cloud.PhoneCloudAction;
import com.micatechnologies.minecraft.sum.phone.cloud.PhoneCloudFetchRequest;
import com.micatechnologies.minecraft.sum.phone.cloud.PhoneCloudSync;
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
        // Slot 7 reserved (was PhonePacketNotesSync — replaced by PhoneCloudAction
        // UPDATE_NOTES, since notes now live in the server cloud, not on the phone item).
        CHANNEL.registerMessage(PhoneCloudFetchRequest.Handler.class,
            PhoneCloudFetchRequest.class, 8, Side.SERVER);
        CHANNEL.registerMessage(PhoneCloudSync.Handler.class,
            PhoneCloudSync.class, 9, Side.CLIENT);
        CHANNEL.registerMessage(PhoneCloudAction.Handler.class,
            PhoneCloudAction.class, 10, Side.SERVER);
        CHANNEL.registerMessage(PacketAddFavorite.Handler.class,
            PacketAddFavorite.class, 11, Side.CLIENT);
        initialized = true;
    }
}
