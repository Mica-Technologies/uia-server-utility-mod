package com.micatechnologies.minecraft.sum.pocket;

import javax.annotation.Nonnull;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Slot container for the pocket GUI: three filtered pocket slots on top + a standard 9x3
 * inventory grid + 9-slot hotbar below. Slot filter is enforced in {@link Slot#isItemValid}
 * via {@link PocketInventory#acceptsStack} so shift-click and number-key swap respect the
 * filter the same way left-drop does.
 *
 * <p>Layout coordinates are in GUI-local units (origin = top-left of the panel BG); the
 * matching {@link GuiPocket} background texture mirrors them.
 */
public class ContainerPocket extends Container {

    /** GUI-local X of the leftmost pocket slot. */
    static final int POCKET_X = 62;
    /** GUI-local Y of the pocket-slots row. */
    static final int POCKET_Y = 17;
    /** Pixel pitch between pocket slots. Matches vanilla 18px slot spacing so the icons
     *  align with whatever ornamentation the BG texture draws. */
    static final int POCKET_STEP = 18;

    /** GUI-local X of the leftmost inventory slot. */
    static final int INV_X = 8;
    /** GUI-local Y of the top inventory row. */
    static final int INV_Y = 51;
    /** GUI-local Y of the hotbar row. */
    static final int HOTBAR_Y = 109;

    private final PocketInventory pocket;
    private final EntityPlayer player;

    public ContainerPocket(EntityPlayer player) {
        this.player = player;
        PocketInventory pi = PocketInventory.get(player);
        // Defensive: AttachCapabilities runs early in player construction so this should
        // never be null for a real player; fall back to a transient instance rather than
        // NPE in case it ever does.
        this.pocket = pi != null ? pi : new PocketInventory();

        for (int i = 0; i < PocketInventory.SLOT_COUNT; i++) {
            addSlotToContainer(new FilteredHandlerSlot(pocket, i,
                POCKET_X + i * POCKET_STEP, POCKET_Y));
        }

        // Standard 3x9 player main inventory.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(player.inventory,
                    col + row * 9 + 9,
                    INV_X + col * 18,
                    INV_Y + row * 18));
            }
        }

        // Hotbar.
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(player.inventory, col,
                INV_X + col * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer playerIn) {
        return playerIn.equals(this.player);
    }

    /**
     * Shift-click handling: from a pocket slot, push the stack into the player's main
     * inventory; from the player inventory or hotbar, push into the first pocket slot that
     * accepts the stack. Returns ItemStack.EMPTY when no further move can happen — vanilla
     * loop sentinel that stops the engine asking us again.
     */
    @Override
    @Nonnull
    public ItemStack transferStackInSlot(@Nonnull EntityPlayer playerIn, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();

        int pocketEnd = PocketInventory.SLOT_COUNT;
        int inventoryEnd = pocketEnd + 36;

        if (index < pocketEnd) {
            // Pocket -> inventory.
            if (!mergeItemStack(stack, pocketEnd, inventoryEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Inventory -> pocket. Walk pocket slots in order; the slot's isItemValid
            // gate prevents inserting into the wrong one.
            if (!mergeItemStack(stack, 0, pocketEnd, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }

        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(playerIn, stack);
        return copy;
    }

    /**
     * Push a fresh sync packet whenever a slot is clicked so the HUD overlay (which reads
     * the synced capability copy on the client) updates the moment the move resolves
     * server-side. {@code Container#slotClick} already mutates the underlying capability
     * via {@link FilteredHandlerSlot} → {@code ItemStackHandler#setStackInSlot}; we just
     * piggy-back on the call to ship the new state.
     */
    @Override
    @Nonnull
    public ItemStack slotClick(int slotId, int dragType, @Nonnull ClickType clickType,
                               @Nonnull EntityPlayer playerIn) {
        ItemStack result = super.slotClick(slotId, dragType, clickType, playerIn);
        if (!playerIn.world.isRemote) {
            PocketEvents.sync(playerIn);
        }
        return result;
    }

    /**
     * SlotItemHandler subclass that also enforces the pocket's per-slot filter on
     * isItemValid (vanilla's default delegates to the handler, which we override too, but
     * shift-click and number-key swap go through this Slot method directly — without the
     * explicit override, those paths bypass the filter).
     */
    private static class FilteredHandlerSlot extends SlotItemHandler {
        private final PocketInventory pocket;
        private final int index;

        FilteredHandlerSlot(PocketInventory pocket, int index, int x, int y) {
            super((IItemHandler) pocket, index, x, y);
            this.pocket = pocket;
            this.index = index;
        }

        @Override
        public boolean isItemValid(@Nonnull ItemStack stack) {
            return stack.isEmpty()
                || PocketInventory.acceptsStack(PocketInventory.Slot.byIndex(index), stack);
        }
    }
}
