package com.micatechnologies.minecraft.sum.economy;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

public class SumMoneyStorage implements Capability.IStorage<ISumMoney> {

    @Override
    public NBTBase writeNBT(Capability<ISumMoney> capability, ISumMoney instance, EnumFacing side) {
        return instance.serializeNBT();
    }

    @Override
    public void readNBT(Capability<ISumMoney> capability, ISumMoney instance, EnumFacing side, NBTBase nbt) {
        if (nbt instanceof NBTTagCompound) {
            instance.deserializeNBT((NBTTagCompound) nbt);
        }
    }
}
