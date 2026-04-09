package com.micatechnologies.minecraft.sum;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;

public class SumTab {

    public static final CreativeTabs TAB = new CreativeTabs(SumConstants.MOD_NAMESPACE) {
        @Override
        public ItemStack createIcon() {
            ItemStack egg = new ItemStack(Items.SPAWN_EGG);
            ItemMonsterPlacer.applyEntityIdToItemStack(
                egg, new ResourceLocation(SumConstants.MOD_NAMESPACE, "roamer"));
            return egg;
        }

        @Override
        public void displayAllRelevantItems(NonNullList<ItemStack> items) {
            super.displayAllRelevantItems(items);
            // Add the roamer spawn egg to this tab
            ItemStack roamerEgg = new ItemStack(Items.SPAWN_EGG);
            ItemMonsterPlacer.applyEntityIdToItemStack(
                roamerEgg, new ResourceLocation(SumConstants.MOD_NAMESPACE, "roamer"));
            items.add(roamerEgg);
        }
    };
}
