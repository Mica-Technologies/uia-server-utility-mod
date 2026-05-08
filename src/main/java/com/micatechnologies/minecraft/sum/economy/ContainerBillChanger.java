package com.micatechnologies.minecraft.sum.economy;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotFurnaceOutput;
import net.minecraft.item.ItemStack;

/**
 * 2-slot changer container (input + output) plus the player's inventory and hotbar.
 * The output slot uses {@link SlotFurnaceOutput} so it can only be removed from, never
 * directly placed into — converted results must come through the Bundle/Unbundle buttons.
 */
public class ContainerBillChanger extends Container {

    private static final int SLOT_SIZE = 18;
    private static final int INPUT_X = 53;
    private static final int INPUT_Y = 24;
    private static final int OUTPUT_X = 107;
    private static final int OUTPUT_Y = 24;

    static final int PLAYER_INV_X = 8;
    static final int PLAYER_INV_Y = 84;
    static final int PLAYER_HOTBAR_Y = 142;
    static final int GUI_HEIGHT = 166;

    private final TileEntityBillChanger te;

    public ContainerBillChanger(TileEntityBillChanger te, EntityPlayer player) {
        this.te = te;

        addSlotToContainer(new Slot(te, TileEntityBillChanger.SLOT_INPUT, INPUT_X, INPUT_Y) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return te.isItemValidForSlot(TileEntityBillChanger.SLOT_INPUT, stack);
            }
        });
        // Output uses the furnace-style result slot — no inserts; only takeouts.
        addSlotToContainer(new SlotFurnaceOutput(player, te, TileEntityBillChanger.SLOT_OUTPUT,
            OUTPUT_X, OUTPUT_Y));

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

    public TileEntityBillChanger getTileEntity() { return te; }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return te.isUsableByPlayer(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        int teEnd = TileEntityBillChanger.INVENTORY_SIZE; // 2
        int playerStart = teEnd;
        int playerEnd = playerStart + 36;

        if (index < teEnd) {
            // Changer slot -> player inventory
            if (!mergeItemStack(stack, playerStart, playerEnd, true)) return ItemStack.EMPTY;
            slot.onSlotChange(stack, original);
        } else {
            // Player -> input slot only (output slot rejects inserts)
            if (!mergeItemStack(stack, TileEntityBillChanger.SLOT_INPUT, TileEntityBillChanger.SLOT_INPUT + 1, false)) {
                return ItemStack.EMPTY;
            }
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
