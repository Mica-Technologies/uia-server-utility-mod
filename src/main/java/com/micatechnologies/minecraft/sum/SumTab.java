package com.micatechnologies.minecraft.sum;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public class SumTab {

    public static final CreativeTabs TAB = new CreativeTabs(SumConstants.MOD_NAMESPACE) {
        @Override
        public ItemStack createIcon() {
            Item egg = Item.REGISTRY.getObject(
                new ResourceLocation(SumConstants.MOD_NAMESPACE, "roamer_spawn_egg"));
            return egg != null ? new ItemStack(egg) : new ItemStack(Items.EGG);
        }
    };
}
