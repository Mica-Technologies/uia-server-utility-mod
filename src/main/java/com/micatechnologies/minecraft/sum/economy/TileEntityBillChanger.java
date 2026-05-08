package com.micatechnologies.minecraft.sum.economy;

import com.micatechnologies.minecraft.sum.atm.Bills;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * State for {@link BlockBillChanger}: a 2-slot inventory (input, output) plus the conversion
 * logic that turns bills into packets and back.
 *
 * <p><b>Bundle.</b> Input must be at least {@link ItemSumPacket#BILLS_PER_PACKET} bills of one
 * denomination. Removes 64 bills, deposits one packet of that denomination into the output.
 *
 * <p><b>Unbundle.</b> Input must be at least one packet. Removes one packet, deposits 64 bills
 * of that denomination into the output (at the changer's preferred backend; the output is
 * always SUM bills since the changer doesn't have to coexist with EconomyInc bills the way
 * the ATM does — but {@link Bills#billItem} returns the EconomyInc variant when it's loaded,
 * so the output stays consistent with what the ATM produces).
 *
 * <p>The output slot must be empty or already contain a stack the result can merge into.
 * Otherwise the conversion is rejected so the caller's input slot is never silently consumed.
 */
public class TileEntityBillChanger extends TileEntity implements IInventory {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int INVENTORY_SIZE = 2;

    private final NonNullList<ItemStack> slots =
        NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);

    public enum ChangeResult {
        OK,
        EMPTY_INPUT,
        UNKNOWN_INPUT,
        NOT_ENOUGH_BILLS,
        NO_PACKET_FOR_DENOM,
        NO_BILL_FOR_DENOM,
        OUTPUT_BLOCKED,
    }

    /** Rolls 64 bills of the input's denomination into one packet. */
    public ChangeResult bundle() {
        ItemStack input = getStackInSlot(SLOT_INPUT);
        if (input.isEmpty()) return ChangeResult.EMPTY_INPUT;

        int denom = Bills.denominationOf(input.getItem());
        if (denom <= 0) return ChangeResult.UNKNOWN_INPUT;

        if (input.getCount() < ItemSumPacket.BILLS_PER_PACKET) {
            return ChangeResult.NOT_ENOUGH_BILLS;
        }

        ItemSumPacket packetItem = ItemSumPacket.packetFor(denom);
        if (packetItem == null) return ChangeResult.NO_PACKET_FOR_DENOM;

        ItemStack result = new ItemStack(packetItem, 1);
        if (!canMergeIntoOutput(result)) return ChangeResult.OUTPUT_BLOCKED;

        input.shrink(ItemSumPacket.BILLS_PER_PACKET);
        if (input.isEmpty()) slots.set(SLOT_INPUT, ItemStack.EMPTY);
        mergeIntoOutput(result);
        markDirty();
        return ChangeResult.OK;
    }

    /** Splits one packet into 64 bills of that denomination. */
    public ChangeResult unbundle() {
        ItemStack input = getStackInSlot(SLOT_INPUT);
        if (input.isEmpty()) return ChangeResult.EMPTY_INPUT;
        if (!(input.getItem() instanceof ItemSumPacket)) return ChangeResult.UNKNOWN_INPUT;

        ItemSumPacket packet = (ItemSumPacket) input.getItem();
        Item billItem = Bills.billItem(packet.getDenomination());
        if (billItem == null) return ChangeResult.NO_BILL_FOR_DENOM;

        ItemStack result = new ItemStack(billItem, ItemSumPacket.BILLS_PER_PACKET);
        if (!canMergeIntoOutput(result)) return ChangeResult.OUTPUT_BLOCKED;

        input.shrink(1);
        if (input.isEmpty()) slots.set(SLOT_INPUT, ItemStack.EMPTY);
        mergeIntoOutput(result);
        markDirty();
        return ChangeResult.OK;
    }

    private boolean canMergeIntoOutput(ItemStack toAdd) {
        ItemStack out = getStackInSlot(SLOT_OUTPUT);
        if (out.isEmpty()) return true;
        if (!ItemStack.areItemsEqual(out, toAdd) || !ItemStack.areItemStackTagsEqual(out, toAdd)) {
            return false;
        }
        return out.getCount() + toAdd.getCount() <= out.getMaxStackSize();
    }

    private void mergeIntoOutput(ItemStack toAdd) {
        ItemStack out = getStackInSlot(SLOT_OUTPUT);
        if (out.isEmpty()) {
            slots.set(SLOT_OUTPUT, toAdd);
        } else {
            out.grow(toAdd.getCount());
        }
    }

    /** Friendly chat-line for a {@link ChangeResult}. */
    public static ITextComponent describe(ChangeResult result) {
        switch (result) {
            case OK: return new TextComponentTranslation("sum.changer.ok");
            case EMPTY_INPUT: return new TextComponentTranslation("sum.changer.empty_input");
            case UNKNOWN_INPUT: return new TextComponentTranslation("sum.changer.unknown_input");
            case NOT_ENOUGH_BILLS:
                return new TextComponentTranslation("sum.changer.not_enough_bills",
                    ItemSumPacket.BILLS_PER_PACKET);
            case NO_PACKET_FOR_DENOM:
                return new TextComponentTranslation("sum.changer.no_packet_for_denom");
            case NO_BILL_FOR_DENOM:
                return new TextComponentTranslation("sum.changer.no_bill_for_denom");
            case OUTPUT_BLOCKED:
                return new TextComponentTranslation("sum.changer.output_blocked");
            default:
                return new TextComponentTranslation("commands.generic.unknown");
        }
    }

    // --- IInventory ---

    @Override public int getSizeInventory() { return INVENTORY_SIZE; }

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
        if (!s.isEmpty()) markDirty();
        return s;
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        ItemStack s = ItemStackHelper.getAndRemove(slots, index);
        if (!s.isEmpty()) markDirty();
        return s;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        slots.set(index, stack);
        if (!stack.isEmpty() && stack.getCount() > getInventoryStackLimit()) {
            stack.setCount(getInventoryStackLimit());
        }
        markDirty();
    }

    @Override public int getInventoryStackLimit() { return 64; }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return world.getTileEntity(pos) == this
            && player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override public void openInventory(EntityPlayer player) {}
    @Override public void closeInventory(EntityPlayer player) {}

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        // Output slot is for results only — players can still take from it but shift-clicking
        // bills into the output is rejected.
        if (index == SLOT_OUTPUT) return false;
        if (stack.isEmpty()) return true;
        // Input accepts bills (any denom) and packets only.
        return Bills.denominationOf(stack.getItem()) > 0
            || stack.getItem() instanceof ItemSumPacket;
    }

    @Override public int getField(int id) { return 0; }
    @Override public void setField(int id, int value) {}
    @Override public int getFieldCount() { return 0; }

    @Override public void clear() { slots.clear(); markDirty(); }

    @Override public String getName() { return "sum.changer.title"; }
    @Override public boolean hasCustomName() { return false; }
    @Override public ITextComponent getDisplayName() {
        return new TextComponentTranslation(getName());
    }

    // --- NBT ---

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        ItemStackHelper.saveAllItems(nbt, slots);
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        slots.clear();
        for (int i = 0; i < slots.size(); i++) slots.set(i, ItemStack.EMPTY);
        ItemStackHelper.loadAllItems(nbt, slots);
    }

    /** Items to spawn when the block is broken. */
    public NonNullList<ItemStack> collectDrops() {
        NonNullList<ItemStack> drops = NonNullList.create();
        for (ItemStack s : slots) {
            if (!s.isEmpty()) drops.add(s.copy());
        }
        return drops;
    }
}
