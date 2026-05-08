package com.micatechnologies.minecraft.sum.economy;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/**
 * State for {@link BlockBillsDisplay}: a single ItemStack of bills or packets, persisted in
 * NBT and synced to the client via {@link SPacketUpdateTileEntity} so the TESR can render
 * the latest count.
 *
 * <p>Insert is "merge if same item type, reject otherwise" so the display never holds a
 * mixed pile (which would render incoherently). Take returns the entire stack at once.
 */
public class TileEntityBillsDisplay extends TileEntity {

    private ItemStack stack = ItemStack.EMPTY;

    public ItemStack getDisplayStack() {
        return stack;
    }

    /**
     * Inserts as much of {@code incoming} as fits. Returns whatever didn't fit (caller is
     * expected to put it back in the player's hand). If the display is empty, accepts any
     * valid stack; if non-empty, only accepts the same item + NBT and stops at maxStackSize.
     */
    public ItemStack insert(ItemStack incoming) {
        if (incoming.isEmpty()) return incoming;
        if (stack.isEmpty()) {
            stack = incoming.copy();
            markDirtyAndSync();
            return ItemStack.EMPTY;
        }
        if (!ItemStack.areItemsEqual(stack, incoming)
            || !ItemStack.areItemStackTagsEqual(stack, incoming)) {
            return incoming;
        }
        int space = stack.getMaxStackSize() - stack.getCount();
        if (space <= 0) return incoming;
        int take = Math.min(space, incoming.getCount());
        stack.grow(take);
        ItemStack remainder = incoming.copy();
        remainder.shrink(take);
        markDirtyAndSync();
        return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
    }

    public ItemStack takeAll() {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = stack;
        stack = ItemStack.EMPTY;
        markDirtyAndSync();
        return out;
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (!stack.isEmpty()) {
            nbt.setTag("Stack", stack.writeToNBT(new NBTTagCompound()));
        }
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        stack = nbt.hasKey("Stack")
            ? new ItemStack(nbt.getCompoundTag("Stack"))
            : ItemStack.EMPTY;
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
