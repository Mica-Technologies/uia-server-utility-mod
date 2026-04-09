package com.micatechnologies.minecraft.sum;

import com.micatechnologies.minecraft.sum.roamer.ItemRoamerSpawnEgg;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

public class SumTab {

    public static final CreativeTabs TAB = new CreativeTabs(SumConstants.MOD_NAMESPACE) {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(SumTab.roamerSpawnEgg);
        }
    };

    public static ItemRoamerSpawnEgg roamerSpawnEgg;

    public static void initTabElements() {
        roamerSpawnEgg = new ItemRoamerSpawnEgg();
    }
}
