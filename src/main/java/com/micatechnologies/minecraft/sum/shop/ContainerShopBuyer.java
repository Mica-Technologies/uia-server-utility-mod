package com.micatechnologies.minecraft.sum.shop;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Buyer-side shop container. The buyer GUI shows a read-only display of what's for sale; this
 * container exposes only the player's inventory + hotbar so the buyer can shift-click bills
 * around or eyeball their stock while shopping.
 *
 * <p>The actual purchase is handled by {@link PacketShopBuy} sent from the GUI's Buy button —
 * no inventory slot operation drives the sale.
 */
public class ContainerShopBuyer extends Container {

    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y = 84;
    private static final int PLAYER_HOTBAR_Y = 142;

    private final TileEntityShop shop;

    public ContainerShopBuyer(TileEntityShop shop, EntityPlayer player) {
        this.shop = shop;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(player.inventory,
                    col + (row + 1) * 9,
                    PLAYER_INV_X + col * SLOT_SIZE,
                    PLAYER_INV_Y + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(player.inventory, col,
                PLAYER_INV_X + col * SLOT_SIZE, PLAYER_HOTBAR_Y));
        }
    }

    public TileEntityShop getShop() { return shop; }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return shop.isUsableByPlayer(player) && !shop.isOwner(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        // No shop slots exposed; shift-click between hotbar and main inv only.
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        int mainEnd = 27;        // first 27 slots are main inventory
        int hotbarEnd = mainEnd + 9;

        if (index < mainEnd) {
            if (!mergeItemStack(stack, mainEnd, hotbarEnd, false)) return ItemStack.EMPTY;
        } else {
            if (!mergeItemStack(stack, 0, mainEnd, false)) return ItemStack.EMPTY;
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
