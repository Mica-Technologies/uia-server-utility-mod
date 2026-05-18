package com.micatechnologies.minecraft.sum.pocket;

import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

/**
 * Per-player {@link PocketInventory} provider. One instance is attached to each
 * {@code EntityPlayer} via {@code AttachCapabilitiesEvent<Entity>} in
 * {@link PocketEvents}.
 */
public class PocketProvider implements ICapabilitySerializable<NBTTagCompound> {

    private final PocketInventory instance = new PocketInventory();

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityPocket.CAPABILITY;
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityPocket.CAPABILITY) {
            return CapabilityPocket.CAPABILITY.cast(instance);
        }
        return null;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return (NBTTagCompound) CapabilityPocket.CAPABILITY.getStorage()
            .writeNBT(CapabilityPocket.CAPABILITY, instance, null);
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        CapabilityPocket.CAPABILITY.getStorage()
            .readNBT(CapabilityPocket.CAPABILITY, instance, null, nbt);
    }
}
