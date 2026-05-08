package com.micatechnologies.minecraft.sum.bank;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Decorative velvet rope stanchion. Always renders the brass post; auto-connects with adjacent
 * {@code BlockVelvetRope} instances in any of the four horizontal directions to render a
 * burgundy rope segment between them. Mirrors the vanilla fence pattern - the four directional
 * properties are computed in {@link #getActualState} and consumed by the multipart blockstate
 * JSON, never stored in metadata.
 */
public class BlockVelvetRope extends Block {

    public static final PropertyBool NORTH = PropertyBool.create("north");
    public static final PropertyBool EAST = PropertyBool.create("east");
    public static final PropertyBool SOUTH = PropertyBool.create("south");
    public static final PropertyBool WEST = PropertyBool.create("west");

    private static final AxisAlignedBB POST_AABB = new AxisAlignedBB(
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
        setDefaultState(blockState.getBaseState()
            .withProperty(NORTH, false)
            .withProperty(EAST, false)
            .withProperty(SOUTH, false)
            .withProperty(WEST, false));

        SumRegistry.registerBlock(this);
        ItemBlock itemBlock = new ItemBlock(this);
        itemBlock.setRegistryName(getRegistryName());
        SumRegistry.registerItem(itemBlock);
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state
            .withProperty(NORTH, isRopeNeighbor(world, pos.north()))
            .withProperty(EAST,  isRopeNeighbor(world, pos.east()))
            .withProperty(SOUTH, isRopeNeighbor(world, pos.south()))
            .withProperty(WEST,  isRopeNeighbor(world, pos.west()));
    }

    private static boolean isRopeNeighbor(IBlockAccess world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof BlockVelvetRope;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return POST_AABB;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, NORTH, EAST, SOUTH, WEST);
    }
}
