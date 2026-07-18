package com.micatechnologies.minecraft.sum.pocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.micatechnologies.minecraft.sum.pocket.PocketInventory.Slot;
import org.junit.jupiter.api.Test;

/**
 * Tests the pure slot enum + per-slot stack limits of {@link PocketInventory}. The item filters
 * ({@code acceptsStack}/{@code isItemValid}) need real bill/account items and are out of scope,
 * but the slot mapping and the bills-slot-stacks-to-64 / others-cap-at-1 rule are pure.
 */
class PocketInventoryTest {

    @Test
    void byIndexMapsTheThreeSlots() {
        assertSame(Slot.PHONE, Slot.byIndex(0));
        assertSame(Slot.DEBIT_CARD, Slot.byIndex(1));
        assertSame(Slot.BILLS, Slot.byIndex(2));
    }

    @Test
    void byIndexReturnsNullOutOfRange() {
        assertNull(Slot.byIndex(-1));
        assertNull(Slot.byIndex(3));
    }

    @Test
    void slotCountIsThree() {
        assertEquals(3, PocketInventory.SLOT_COUNT);
    }

    @Test
    void billsSlotStacksToSixtyFourOthersToOne() {
        PocketInventory pocket = new PocketInventory();
        assertEquals(64, pocket.getSlotLimit(Slot.BILLS.index));
        assertEquals(1, pocket.getSlotLimit(Slot.PHONE.index));
        assertEquals(1, pocket.getSlotLimit(Slot.DEBIT_CARD.index));
    }

    @Test
    void unknownSlotFallsBackToLimitOne() {
        // byIndex(3) → null → not BILLS → the belt-and-suspenders cap of 1.
        assertEquals(1, new PocketInventory().getSlotLimit(3));
    }
}
