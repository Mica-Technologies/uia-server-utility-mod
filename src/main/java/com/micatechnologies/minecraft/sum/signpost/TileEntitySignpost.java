package com.micatechnologies.minecraft.sum.signpost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants;

public class TileEntitySignpost extends TileEntity {

    public static final int MAX_ARMS = 7;

    private static final String NBT_ARMS = "arms";

    private final List<SignpostArm> arms = new ArrayList<>(MAX_ARMS);

    public List<SignpostArm> getArms() {
        return Collections.unmodifiableList(arms);
    }

    public boolean addArm(SignpostArm arm) {
        if (arm == null || arms.size() >= MAX_ARMS) {
            return false;
        }
        arms.add(arm);
        sync();
        return true;
    }

    public boolean removeArm(int index) {
        if (index < 0 || index >= arms.size()) {
            return false;
        }
        arms.remove(index);
        sync();
        return true;
    }

    public boolean updateArm(int index, String label, Float angleDegrees) {
        if (index < 0 || index >= arms.size()) {
            return false;
        }
        SignpostArm arm = arms.get(index);
        if (label != null) {
            arm.setLabel(label);
        }
        if (angleDegrees != null) {
            arm.setAngleDegrees(angleDegrees);
        }
        sync();
        return true;
    }

    public void clearArms() {
        arms.clear();
        sync();
    }

    private void sync() {
        markDirty();
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        arms.clear();
        NBTTagList list = compound.getTagList(NBT_ARMS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount() && arms.size() < MAX_ARMS; i++) {
            arms.add(SignpostArm.deserialize(list.getCompoundTagAt(i)));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        NBTTagList list = new NBTTagList();
        for (SignpostArm arm : arms) {
            list.appendTag(arm.serialize());
        }
        compound.setTag(NBT_ARMS, list);
        return compound;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }
}
