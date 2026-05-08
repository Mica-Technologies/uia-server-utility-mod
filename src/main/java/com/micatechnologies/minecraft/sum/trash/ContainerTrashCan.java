package com.micatechnologies.minecraft.sum.trash;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 9-slot trash container (single row) plus the player's inventory + hotbar. The trash
 * inventory is owned by this container instance and discarded when the GUI closes — that's
 * how items get destroyed. We also explicitly clear it in {@link #onContainerClosed} so the
 * server doesn't keep references that could leak via debug tooling.
 */
public class ContainerTrashCan extends Container {

    public static final int TRASH_SLOTS = 9;

    private static final int SLOT_SIZE = 18;

    private final IInventory trashInventory;

    public ContainerTrashCan(IInventory trashInventory, EntityPlayer player) {
        this.trashInventory = trashInventory;

        // 1x9 trash slots, top-left at (8, 17)
        for (int col = 0; col < TRASH_SLOTS; col++) {
            addSlotToContainer(new Slot(trashInventory, col, 8 + col * SLOT_SIZE, 17));
        }

        // Player main inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(player.inventory,
                    col + (row + 1) * 9,
                    8 + col * SLOT_SIZE,
                    51 + row * SLOT_SIZE));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(player.inventory, col, 8 + col * SLOT_SIZE, 109));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        // Wipe the trash inventory so its contents are unreachable. The IInventory is also
        // about to be garbage-collected (the Container is the only strong reference), but
        // explicit clear matches the user's mental model: "I closed the lid; the trash is gone."
        for (int i = 0; i < trashInventory.getSizeInventory(); i++) {
            trashInventory.setInventorySlotContents(i, ItemStack.EMPTY);
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        int trashEnd = TRASH_SLOTS;
        int playerStart = trashEnd;
        int playerEnd = playerStart + 36;

        if (index < trashEnd) {
            // Trash -> player inventory (rescue an item before close)
            if (!mergeItemStack(stack, playerStart, playerEnd, true)) return ItemStack.EMPTY;
        } else {
            // Player -> trash slots
            if (!mergeItemStack(stack, 0, trashEnd, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
