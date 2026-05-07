package com.micatechnologies.minecraft.sum.economy;

import com.micatechnologies.minecraft.sum.SumConstants;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;

/**
 * Capability holder for {@link ISumMoney}. Mirrors EconomyInc's {@code CapabilityLoading}
 * pattern but for SUM's own balance system.
 *
 * <p>Call {@link #register()} once from {@code Sum.preInit} before any player capability
 * lookups happen.
 */
public final class CapabilitySumMoney {

    @CapabilityInject(ISumMoney.class)
    public static Capability<ISumMoney> CAPABILITY = null;

    /** ResourceLocation key used when attaching the provider via AttachCapabilitiesEvent. */
    public static final ResourceLocation KEY = new ResourceLocation(SumConstants.MOD_NAMESPACE, "money");

    private CapabilitySumMoney() {}

    public static void register() {
        CapabilityManager.INSTANCE.register(ISumMoney.class, new SumMoneyStorage(), DefaultSumMoney::new);
    }
}
