package com.micatechnologies.minecraft.sum.bank;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Decorative velvet rope stanchion - a thin brass pole with a knob on top, occupying a
 * 4x16x4 cuboid centered in its block. Players line them up to make queue lines.
 *
 * <p>v1 is a single pole only - no auto-connecting rope between adjacent stanchions. The
 * rope between two posts is implied visually rather than rendered. A future polish pass can
 * add fence-style neighbor detection for actual rope rendering.
 */
public class BlockVelvetRope extends Block {

    private static final AxisAlignedBB BB = new AxisAlignedBB(
        6.0 / 16.0, 0.0, 6.0 / 16.0,
        10.0 / 16.0, 1.0, 10.0 / 16.0);

    public BlockVelvetRope() {
        super(Material.IRON);
        setRegistryName(SumConstants.MOD_NAMESPACE, "velvet_rope");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".velvet_rope");
        setHardness(1.0F);
        setResistance(2.0F);
        setSoundType(SoundType.METAL);
        setCreativeTab(SumTab.TAB);

        SumRegistry.registerBlock(this);
        ItemBlock itemBlock = new ItemBlock(this);
        itemBlock.setRegistryName(getRegistryName());
        SumRegistry.registerItem(itemBlock);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BB;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }
}
