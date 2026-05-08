package com.micatechnologies.minecraft.sum.mailbox;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * State for {@link BlockMailbox}: owner UUID + name + a 9-slot inbox. The first player to
 * right-click claims ownership; afterwards the box is split between two access modes — only
 * the owner can take items out, but any player can deposit into the inbox.
 *
 * <p>Items in the inbox persist via NBT and survive chunk unloads / restarts, so an offline
 * recipient still gets their mail.
 */
public class TileEntityMailbox extends TileEntity implements IInventory {

    public static final int INBOX_SIZE = 9;

    private final NonNullList<ItemStack> slots =
        NonNullList.withSize(INBOX_SIZE, ItemStack.EMPTY);

    @Nullable
    private UUID ownerUuid;
    private String ownerName = "";

    public boolean isClaimed() { return ownerUuid != null; }

    @Nullable
    public UUID getOwnerUuid() { return ownerUuid; }

    public String getOwnerName() { return ownerName; }

    public boolean isOwner(EntityPlayer player) {
        return ownerUuid != null && ownerUuid.equals(player.getUniqueID());
    }

    /** Claim an unowned mailbox for {@code player}. Returns false on already-claimed boxes. */
    public boolean claim(EntityPlayer player) {
        if (ownerUuid != null) return false;
        this.ownerUuid = player.getUniqueID();
        this.ownerName = player.getName();
        markDirtyAndSync();
        return true;
    }

    public void disown() {
        this.ownerUuid = null;
        this.ownerName = "";
        markDirtyAndSync();
    }

    /** True when the inbox has at least one non-empty slot — used by the login notifier. */
    public boolean hasMail() {
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) return true;
        }
        return false;
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    // --- IInventory ---

    @Override public int getSizeInventory() { return INBOX_SIZE; }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : slots) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return index < 0 || index >= slots.size() ? ItemStack.EMPTY : slots.get(index);
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        ItemStack s = ItemStackHelper.getAndSplit(slots, index, count);
        if (!s.isEmpty()) markDirtyAndSync();
        return s;
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        ItemStack s = ItemStackHelper.getAndRemove(slots, index);
        if (!s.isEmpty()) markDirtyAndSync();
        return s;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        slots.set(index, stack);
        if (!stack.isEmpty() && stack.getCount() > getInventoryStackLimit()) {
            stack.setCount(getInventoryStackLimit());
        }
        markDirtyAndSync();
    }

    @Override public int getInventoryStackLimit() { return 64; }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return world.getTileEntity(pos) == this
            && player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override public void openInventory(EntityPlayer player) {}
    @Override public void closeInventory(EntityPlayer player) {}
    @Override public boolean isItemValidForSlot(int index, ItemStack stack) { return true; }
    @Override public int getField(int id) { return 0; }
    @Override public void setField(int id, int value) {}
    @Override public int getFieldCount() { return 0; }

    @Override public void clear() { slots.clear(); markDirtyAndSync(); }

    @Override public String getName() { return "sum.mailbox.title"; }
    @Override public boolean hasCustomName() { return false; }
    @Override public ITextComponent getDisplayName() {
        return new TextComponentTranslation(getName());
    }

    // --- NBT + sync ---

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (ownerUuid != null) nbt.setUniqueId("owner", ownerUuid);
        nbt.setString("ownerName", ownerName);
        ItemStackHelper.saveAllItems(nbt, slots);
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.ownerUuid = nbt.hasUniqueId("owner") ? nbt.getUniqueId("owner") : null;
        this.ownerName = nbt.getString("ownerName");
        slots.clear();
        for (int i = 0; i < slots.size(); i++) slots.set(i, ItemStack.EMPTY);
        ItemStackHelper.loadAllItems(nbt, slots);
    }

    @Override
    public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    public NonNullList<ItemStack> collectDrops() {
        NonNullList<ItemStack> drops = NonNullList.create();
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) drops.add(stack.copy());
        }
        return drops;
    }
}
