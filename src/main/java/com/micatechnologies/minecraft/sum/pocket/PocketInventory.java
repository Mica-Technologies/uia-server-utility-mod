package com.micatechnologies.minecraft.sum.pocket;

import com.micatechnologies.minecraft.sum.atm.Bills;
import com.micatechnologies.minecraft.sum.economy.ItemAccountAccess;
import javax.annotation.Nonnull;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Per-player "pocket" — three filtered slots that complement the vanilla inventory: one
 * for the SUM phone, one for the SUM debit card, one for SUM bills. Attached to every
 * player via {@link CapabilityPocket} and persisted across death / dimension change /
 * world save by the standard capability serialization path.
 *
 * <p>Slot layout is fixed (see {@link Slot}); each slot's filter is enforced both server-
 * side ({@link #isItemValid}) and client-side (Container slot rendering). The bills slot
 * is stackable up to 64 of one denomination; the phone and card slots cap at 1 (which
 * matches the items' own {@code maxStackSize}).
 */
public class PocketInventory extends ItemStackHandler {

    /** Symbolic slot indexes — kept close to the array order so a misread is unlikely. */
    public enum Slot {
        PHONE(0),
        DEBIT_CARD(1),
        BILLS(2);

        public final int index;

        Slot(int index) {
            this.index = index;
        }

        public static Slot byIndex(int index) {
            for (Slot s : values()) {
                if (s.index == index) {
                    return s;
                }
            }
            return null;
        }
    }

    public static final int SLOT_COUNT = Slot.values().length;

    public PocketInventory() {
        super(SLOT_COUNT);
    }

    /**
     * Convenience accessor — looks up the pocket capability on a player.
     *
     * @return the player's pocket inventory, or {@code null} if the capability isn't
     *     attached (shouldn't happen for live EntityPlayer instances; defensive null is
     *     for FakePlayer / pre-attach edge cases).
     */
    public static PocketInventory get(EntityPlayer player) {
        if (player == null) {
            return null;
        }
        return player.getCapability(CapabilityPocket.CAPABILITY, null);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        return acceptsStack(Slot.byIndex(slot), stack);
    }

    /**
     * Static filter, exposed so the container's Slot subclass and HUD edit-mode tooltips
     * can reuse the same predicate. Null {@code slot} means "any slot" — useful when
     * checking whether a stack could plausibly land in the pocket at all.
     */
    public static boolean acceptsStack(Slot slot, ItemStack stack) {
        if (stack == null || stack.isEmpty() || slot == null) {
            return false;
        }
        Item item = stack.getItem();
        switch (slot) {
            case PHONE:
                return item instanceof ItemAccountAccess
                    && ((ItemAccountAccess) item).isPhone();
            case DEBIT_CARD:
                return item instanceof ItemAccountAccess
                    && !((ItemAccountAccess) item).isPhone();
            case BILLS:
                // Bills.denominationOf returns >0 for any SUM or EconomyInc bill item.
                return Bills.denominationOf(item) > 0;
            default:
                return false;
        }
    }

    @Override
    public int getSlotLimit(int slot) {
        Slot s = Slot.byIndex(slot);
        if (s == Slot.BILLS) {
            return 64;
        }
        // Phone and debit card stacks are 1-max by their own setMaxStackSize; the slot
        // limit is a belt-and-suspenders cap so a malformed insert can't ever stash more.
        return 1;
    }
}
