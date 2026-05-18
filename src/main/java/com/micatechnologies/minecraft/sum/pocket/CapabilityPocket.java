package com.micatechnologies.minecraft.sum.pocket;

import com.micatechnologies.minecraft.sum.SumConstants;
import javax.annotation.Nullable;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;

/**
 * Capability holder for {@link PocketInventory}. One instance is attached per player by
 * {@link PocketEvents#attachCapabilities}; {@link #register()} must run once during
 * {@code Sum.preInit} (before any player capability lookups).
 */
public final class CapabilityPocket {

    @CapabilityInject(PocketInventory.class)
    public static Capability<PocketInventory> CAPABILITY = null;

    /** ResourceLocation key used by {@link PocketEvents} when attaching the provider. */
    public static final ResourceLocation KEY =
        new ResourceLocation(SumConstants.MOD_NAMESPACE, "pocket");

    private CapabilityPocket() {}

    public static void register() {
        CapabilityManager.INSTANCE.register(
            PocketInventory.class,
            new Storage(),
            PocketInventory::new);
    }

    /**
     * Trivial NBT bridge — {@link PocketInventory} already extends {@code ItemStackHandler}
     * which implements {@code INBTSerializable<NBTTagCompound>}, so the storage just hands
     * off to that.
     */
    public static final class Storage implements Capability.IStorage<PocketInventory> {
        @Override
        @Nullable
        public NBTBase writeNBT(Capability<PocketInventory> capability,
                                PocketInventory instance,
                                @Nullable EnumFacing side) {
            return instance.serializeNBT();
        }

        @Override
        public void readNBT(Capability<PocketInventory> capability,
                            PocketInventory instance,
                            @Nullable EnumFacing side,
                            NBTBase nbt) {
            if (nbt instanceof NBTTagCompound) {
                instance.deserializeNBT((NBTTagCompound) nbt);
            }
        }
    }
}
