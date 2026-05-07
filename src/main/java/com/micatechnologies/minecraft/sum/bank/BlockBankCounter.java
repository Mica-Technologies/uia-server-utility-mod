package com.micatechnologies.minecraft.sum.bank;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemBlock;

/**
 * Decorative bank-counter block. Solid full cube with a polished green-marble counter top
 * sitting on a dark walnut body. Symmetric on all four horizontal sides so it can be lined up
 * to make a continuous teller line without worrying about facing.
 */
public class BlockBankCounter extends Block {

    public BlockBankCounter() {
        super(Material.WOOD);
        setRegistryName(SumConstants.MOD_NAMESPACE, "bank_counter");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".bank_counter");
        setHardness(2.0F);
        setResistance(5.0F);
        setSoundType(SoundType.WOOD);
        setCreativeTab(SumTab.TAB);

        SumRegistry.registerBlock(this);
        ItemBlock itemBlock = new ItemBlock(this);
        itemBlock.setRegistryName(getRegistryName());
        SumRegistry.registerItem(itemBlock);
    }
}
