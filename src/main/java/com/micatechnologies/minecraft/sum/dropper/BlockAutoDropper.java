package com.micatechnologies.minecraft.sum.dropper;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import java.util.Random;
import net.minecraft.block.BlockDropper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Auto Dropper — vanilla dropper inventory/UI, but ticks on its own and dispenses without
 * scatter. Halts when redstone-powered (inverse of vanilla, where powered = fire). Uses
 * {@link TileEntityAutoDropper} for the tick logic; this block exists only to swap the TE
 * and short out vanilla's redstone-pulse-triggers-dispense path.
 */
public class BlockAutoDropper extends BlockDropper {

    public BlockAutoDropper() {
        super();
        setRegistryName(SumConstants.MOD_NAMESPACE, "auto_dropper");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".auto_dropper");
        setCreativeTab(SumTab.TAB);

        SumRegistry.registerBlock(this);
        ItemBlock itemBlock = new ItemBlock(this);
        itemBlock.setRegistryName(getRegistryName());
        SumRegistry.registerItem(itemBlock);
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEntityAutoDropper();
    }

    /** Vanilla schedules an updateTick on power; we don't want that pathway. The TE's own
     *  tick handler reads {@code world.isBlockPowered(pos)} directly each cycle. */
    @Override
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos,
                                net.minecraft.block.Block blockIn, BlockPos fromPos) {
    }

    /** Belt-and-suspenders: even if something else triggers updateTick, don't dispense. */
    @Override
    public void updateTick(World worldIn, BlockPos pos, IBlockState state, Random rand) {
    }
}
