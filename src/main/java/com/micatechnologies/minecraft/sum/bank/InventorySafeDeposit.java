package com.micatechnologies.minecraft.sum.bank;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;

/**
 * IInventory wrapper around a 9-slot list backed by {@link SafeDepositSavedData}. On every
 * mutation, writes the current contents back to the SavedData and marks it dirty so the
 * world will persist the change.
 *
 * <p>The client copy of this inventory uses a null {@code savedData} - mutations there are
 * informational only (the server is the source of truth) and the markDirty write-back is
 * skipped.
 */
public class InventorySafeDeposit extends InventoryBasic {

    @Nullable
    private final SafeDepositSavedData savedData;
    private final UUID owner;
    private final BlockPos pos;

    public InventorySafeDeposit(UUID owner, BlockPos pos, @Nullable SafeDepositSavedData savedData,
                                NonNullList<ItemStack> backing) {
        super("sum.safe_deposit.title", false, SafeDepositSavedData.SLOT_COUNT);
        this.owner = owner;
        this.pos = pos;
        this.savedData = savedData;
        for (int i = 0; i < backing.size() && i < this.getSizeInventory(); i++) {
            super.setInventorySlotContents(i, backing.get(i));
        }
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (savedData == null) {
            return;
        }
        NonNullList<ItemStack> backing = savedData.getInventory(owner, pos);
        for (int i = 0; i < this.getSizeInventory(); i++) {
            backing.set(i, this.getStackInSlot(i));
        }
        savedData.markDirty();
    }
}
