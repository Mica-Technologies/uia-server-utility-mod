package com.micatechnologies.minecraft.sum.bank;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 3x3 safe-deposit-box container + player inventory + hotbar. Layout matches vanilla's
 * dispenser GUI so we can reuse {@code textures/gui/container/dispenser.png} as the
 * background.
 */
public class ContainerSafeDeposit extends Container {

    private static final int BOX_ROWS = 3;
    private static final int BOX_COLS = 3;
    private static final int BOX_SLOT_SIZE = 18;

    private final IInventory boxInventory;

    public ContainerSafeDeposit(IInventory boxInventory, EntityPlayer player) {
        this.boxInventory = boxInventory;
        boxInventory.openInventory(player);

        // 3x3 box slots, top-left at (62, 17)
        for (int row = 0; row < BOX_ROWS; row++) {
            for (int col = 0; col < BOX_COLS; col++) {
                addSlotToContainer(new Slot(boxInventory,
                    col + row * BOX_COLS,
                    62 + col * BOX_SLOT_SIZE,
                    17 + row * BOX_SLOT_SIZE));
            }
        }

        // Player main inventory (3 rows x 9 cols), top-left at (8, 84)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(player.inventory,
                    col + (row + 1) * 9,
                    8 + col * BOX_SLOT_SIZE,
                    84 + row * BOX_SLOT_SIZE));
            }
        }

        // Hotbar (1 row x 9 cols), at y=142
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(player.inventory, col, 8 + col * BOX_SLOT_SIZE, 142));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return boxInventory.isUsableByPlayer(player);
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        boxInventory.closeInventory(player);
        // Final flush in case the last mutation didn't trigger markDirty.
        boxInventory.markDirty();
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        int boxSize = BOX_ROWS * BOX_COLS;
        int playerStart = boxSize;
        int playerEnd = playerStart + 36;

        if (index < boxSize) {
            // Box -> player inventory
            if (!mergeItemStack(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player inventory -> box
            if (!mergeItemStack(stack, 0, boxSize, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }
}
