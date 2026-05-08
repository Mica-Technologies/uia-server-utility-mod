package com.micatechnologies.minecraft.sum.economy;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import net.minecraft.item.Item;

/**
 * SUM-native currency bill. One instance per denomination ($1, $5, $10, $20, $50, $100, $200,
 * $500); registered as {@code sum:bill_<denomination>}. Bills are physical money items - they
 * stack to 64, can be dropped, traded, stored in chests. The dollar value is attached to the
 * Item itself, so identifying a stack's denomination is a simple
 * {@code stack.getItem() instanceof ItemSumBill}.
 *
 * <p>SUM bills coexist with EconomyInc bills when both mods are loaded. {@link Bills} accepts
 * both registry namespaces transparently.
 */
public class ItemSumBill extends Item {

    private final int denomination;

    public ItemSumBill(int denomination) {
        this.denomination = denomination;
        setRegistryName(SumConstants.MOD_NAMESPACE, "bill_" + denomination);
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".bill_" + denomination);
        setMaxStackSize(64);
        setCreativeTab(SumTab.TAB);

        SumRegistry.registerItem(this);
    }

    public int getDenomination() {
        return denomination;
    }
}
