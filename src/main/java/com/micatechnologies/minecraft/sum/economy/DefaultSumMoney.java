package com.micatechnologies.minecraft.sum.economy;

import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

public class DefaultSumMoney implements ISumMoney {

    private static final String NBT_KEY_BALANCE = "balance";

    private double balance;

    @Override
    public double getBalance() {
        return balance;
    }

    @Override
    public void setBalance(double balance) {
        this.balance = Math.max(0.0, balance);
    }

    @Override
    public boolean adjust(double delta) {
        double next = balance + delta;
        if (next < 0.0) {
            return false;
        }
        this.balance = next;
        return true;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setDouble(NBT_KEY_BALANCE, balance);
        return nbt;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        this.balance = nbt.hasKey(NBT_KEY_BALANCE) ? nbt.getDouble(NBT_KEY_BALANCE) : 0.0;
    }

    @Override
    public void sync(EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            SumNetwork.CHANNEL.sendTo(new PacketSyncSumMoney(balance), (EntityPlayerMP) player);
        }
    }
}
