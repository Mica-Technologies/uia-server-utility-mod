package com.micatechnologies.minecraft.sum.economy;

import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

/**
 * Per-player {@link ISumMoney} provider. One instance is attached to each {@code EntityPlayer}
 * via {@code AttachCapabilitiesEvent<Entity>} in {@link SumMoneyEvents}. The instance is held
 * for the lifetime of that player entity (server-side, persisted to {@code playerdata/<uuid>.dat};
 * client-side, refreshed by the sync packet on login + after each mutation).
 */
public class SumMoneyProvider implements ICapabilitySerializable<NBTTagCompound> {

    private final ISumMoney instance = new DefaultSumMoney();

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilitySumMoney.CAPABILITY;
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilitySumMoney.CAPABILITY) {
            return CapabilitySumMoney.CAPABILITY.cast(instance);
        }
        return null;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return (NBTTagCompound) CapabilitySumMoney.CAPABILITY.getStorage()
            .writeNBT(CapabilitySumMoney.CAPABILITY, instance, null);
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        CapabilitySumMoney.CAPABILITY.getStorage()
            .readNBT(CapabilitySumMoney.CAPABILITY, instance, null, nbt);
    }
}
