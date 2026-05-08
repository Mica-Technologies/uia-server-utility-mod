package com.micatechnologies.minecraft.sum.shop;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Owner-side shop container. Exposes the shop's 10 inventory slots (1 template + 9 stock) plus
 * the player's hotbar/inventory. Buy/sell config + funds withdraw are handled out-of-band via
 * {@link PacketShopOwnerAction} (no slots required for those).
 *
 * <p>Layout uses the same coordinate basis as a vanilla 3x3 dispenser GUI; the template slot
 * sits to the left of the stock grid.
 */
public class ContainerShopOwner extends Container {

    private static final int SLOT_SIZE = 18;

    private static final int TEMPLATE_X = 26;
    private static final int TEMPLATE_Y = 17;
    private static final int STOCK_X = 80;
    private static final int STOCK_Y = 17;

    private static final int PLAYER_INV_X = 8;
    static final int PLAYER_INV_Y = 144;
    static final int PLAYER_HOTBAR_Y = 202;
    static final int GUI_HEIGHT = 226;

    private final TileEntityShop shop;

    public ContainerShopOwner(TileEntityShop shop, EntityPlayer player) {
        this.shop = shop;

        // Template slot (slot 0) — single slot to the left, framed visually as the "what we sell"
        // sample. Accepts any item stack (TE.setInventorySlotContents handles markDirty + sync).
        addSlotToContainer(new Slot(shop, TileEntityShop.TEMPLATE_SLOT, TEMPLATE_X, TEMPLATE_Y));

        // Stock grid (slots 1..9), 3x3
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slotIdx = TileEntityShop.STOCK_SLOT_FIRST + col + row * 3;
                addSlotToContainer(new Slot(shop, slotIdx,
                    STOCK_X + col * SLOT_SIZE,
                    STOCK_Y + row * SLOT_SIZE));
            }
        }

        // Player main inventory (3 rows x 9 cols)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(player.inventory,
                    col + (row + 1) * 9,
                    PLAYER_INV_X + col * SLOT_SIZE,
                    PLAYER_INV_Y + row * SLOT_SIZE));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(player.inventory, col,
                PLAYER_INV_X + col * SLOT_SIZE,
                PLAYER_HOTBAR_Y));
        }
    }

    public TileEntityShop getShop() { return shop; }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return shop.isUsableByPlayer(player) && shop.isOwner(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        int shopEnd = TileEntityShop.INVENTORY_SIZE; // 10
        int playerStart = shopEnd;
        int playerEnd = playerStart + 36;

        if (index < shopEnd) {
            // Shop -> player inventory
            if (!mergeItemStack(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player -> stock grid first, then template if grid full
            if (!mergeItemStack(stack, TileEntityShop.STOCK_SLOT_FIRST, shopEnd, false)
                && !mergeItemStack(stack, 0, 1, false)) {
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
