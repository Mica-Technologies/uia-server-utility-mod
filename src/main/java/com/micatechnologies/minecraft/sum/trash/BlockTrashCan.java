package com.micatechnologies.minecraft.sum.trash;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import com.micatechnologies.minecraft.sum.atm.SumGuiHandler;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Trash can block — right-click to open a 9-slot ephemeral inventory. Anything left in the
 * inventory when the GUI is closed is permanently destroyed. No tile entity; the inventory
 * is allocated fresh on each open and never persisted, so closing-then-reopening always
 * shows an empty bin (no rescue path once you walk away from the GUI).
 */
public class BlockTrashCan extends Block {

    public BlockTrashCan() {
        super(Material.IRON);
        setRegistryName(SumConstants.MOD_NAMESPACE, "trash_can");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".trash_can");
        setHardness(1.5F);
        setResistance(10.0F);
        setSoundType(SoundType.METAL);
        setCreativeTab(SumTab.TAB);

        SumRegistry.registerBlock(this);
        ItemBlock itemBlock = new ItemBlock(this);
        itemBlock.setRegistryName(getRegistryName());
        SumRegistry.registerItem(itemBlock);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(Sum.instance, SumGuiHandler.GUI_TRASH_CAN, world,
                pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }
}
