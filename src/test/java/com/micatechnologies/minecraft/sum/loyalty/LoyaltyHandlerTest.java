package com.micatechnologies.minecraft.sum.loyalty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import org.junit.jupiter.api.Test;

/**
 * Tests the pure helpers of {@link LoyaltyHandler}: {@code formatAmount} (whole numbers drop the
 * decimals) and {@code containsInt} (the "fire each milestone once" dedup over the fired-list NBT).
 * The reward-firing orchestration itself needs a live player + economy and is out of scope.
 */
class LoyaltyHandlerTest {

    @Test
    void formatAmountDropsDecimalsForWholeNumbers() {
        assertEquals("100", LoyaltyHandler.formatAmount(100.0));
        assertEquals("0", LoyaltyHandler.formatAmount(0.0));
    }

    @Test
    void formatAmountKeepsTwoDecimalsOtherwise() {
        assertEquals("100.50", LoyaltyHandler.formatAmount(100.5));
        assertEquals("99.99", LoyaltyHandler.formatAmount(99.99));
    }

    @Test
    void containsIntFindsMembers() {
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagInt(30));
        list.appendTag(new NBTTagInt(60));
        assertTrue(LoyaltyHandler.containsInt(list, 60));
        assertFalse(LoyaltyHandler.containsInt(list, 45));
    }

    @Test
    void containsIntOnEmptyListIsFalse() {
        assertFalse(LoyaltyHandler.containsInt(new NBTTagList(), 30));
    }
}
