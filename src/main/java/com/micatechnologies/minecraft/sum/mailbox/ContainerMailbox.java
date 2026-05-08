package com.micatechnologies.minecraft.sum.mailbox;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Mailbox container. The visual layout is the same for owner and non-owner; the difference is
 * a read/write toggle on the inbox slots:
 *
 * <ul>
 *   <li>Owner: standard slots — can deposit and take.</li>
 *   <li>Non-owner: {@link DepositOnlySlot} — can deposit (shift-click and drag both work), but
 *       {@code canTakeStack} returns false so they can't pull anything out.</li>
 * </ul>
 *
 * <p>Both views show the inbox contents — that's a deliberate trade-off: non-owners can see
 * what's already in the box, which means they can avoid duplicating an item that's already
 * been delivered. Privacy-sensitive servers can swap in an admin-only "private mailbox"
 * variant later.
 */
public class ContainerMailbox extends Container {

    private static final int SLOT_SIZE = 18;
    private static final int INBOX_X = 8;
    private static final int INBOX_Y = 18;
    private static final int PLAYER_INV_X = 8;
    static final int PLAYER_INV_Y = 56;
    static final int PLAYER_HOTBAR_Y = 114;
    static final int GUI_HEIGHT = 138;

    private final TileEntityMailbox mailbox;
    private final boolean ownerView;

    public ContainerMailbox(TileEntityMailbox mailbox, EntityPlayer player) {
        this.mailbox = mailbox;
        this.ownerView = mailbox.isOwner(player);

        for (int col = 0; col < TileEntityMailbox.INBOX_SIZE; col++) {
            if (ownerView) {
                addSlotToContainer(new Slot(mailbox, col,
                    INBOX_X + col * SLOT_SIZE, INBOX_Y));
            } else {
                addSlotToContainer(new DepositOnlySlot(mailbox, col,
                    INBOX_X + col * SLOT_SIZE, INBOX_Y));
            }
        }
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

    public TileEntityMailbox getMailbox() { return mailbox; }
    public boolean isOwnerView() { return ownerView; }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return mailbox.isUsableByPlayer(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        int boxEnd = TileEntityMailbox.INBOX_SIZE;
        int playerStart = boxEnd;
        int playerEnd = playerStart + 36;

        if (index < boxEnd) {
            // Box -> player. Only the owner can take, so non-owner shift-clicks on the box
            // are rejected (DepositOnlySlot.canTakeStack returns false, so the slot won't
            // even hand its stack over to mergeItemStack).
            if (!ownerView) return ItemStack.EMPTY;
            if (!mergeItemStack(stack, playerStart, playerEnd, true)) return ItemStack.EMPTY;
        } else {
            // Player -> box. Allowed for everyone.
            if (!mergeItemStack(stack, 0, boxEnd, false)) return ItemStack.EMPTY;
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

    /** Slot variant that accepts deposits but never lets the holder remove items. */
    private static class DepositOnlySlot extends Slot {
        DepositOnlySlot(net.minecraft.inventory.IInventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return false;
        }
    }
}
