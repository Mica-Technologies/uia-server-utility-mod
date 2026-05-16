package com.micatechnologies.minecraft.sum.bank;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import com.micatechnologies.minecraft.sum.atm.SumGuiHandler;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Wall-mounted safe deposit box. Right-clicking opens a 3x3 inventory that is unique to the
 * player who right-clicked - different players accessing the same block see different
 * contents. Storage persists per (player UUID, block position) via
 * {@link SafeDepositSavedData}.
 *
 * <p>Like an EnderChest, but located: the box is tied to a specific spot in the world (so a
 * player can't access it from anywhere) but the contents are personal (so two players don't
 * step on each other's items).
 */
public class BlockSafeDepositBox extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    // Wall-mounted thin-shell footprint - the back of the box hugs the wall it was placed
    // on (opposite side of the cell from FACING), so the front face is visible to the player.
    private static final AxisAlignedBB BB_NORTH = new AxisAlignedBB(
        3.0 / 16.0, 0.0, 11.0 / 16.0,
        13.0 / 16.0, 1.0, 1.0);
    private static final AxisAlignedBB BB_SOUTH = new AxisAlignedBB(
        3.0 / 16.0, 0.0, 0.0,
        13.0 / 16.0, 1.0, 5.0 / 16.0);
    private static final AxisAlignedBB BB_WEST = new AxisAlignedBB(
        11.0 / 16.0, 0.0, 3.0 / 16.0,
        1.0, 1.0, 13.0 / 16.0);
    private static final AxisAlignedBB BB_EAST = new AxisAlignedBB(
        0.0, 0.0, 3.0 / 16.0,
        5.0 / 16.0, 1.0, 13.0 / 16.0);

    public BlockSafeDepositBox() {
        super(Material.IRON);
        setRegistryName(SumConstants.MOD_NAMESPACE, "safe_deposit_box");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".safe_deposit_box");
        setHardness(3.0F);
        setResistance(15.0F);
        setSoundType(SoundType.METAL);
        setCreativeTab(SumTab.TAB);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));

        SumRegistry.registerBlock(this);
        ItemBlock itemBlock = new ItemBlock(this);
        itemBlock.setRegistryName(getRegistryName());
        SumRegistry.registerItem(itemBlock);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(Sum.instance, SumGuiHandler.GUI_SAFE_DEPOSIT, world,
                pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        switch (state.getValue(FACING)) {
            case SOUTH: return BB_SOUTH;
            case WEST:  return BB_WEST;
            case EAST:  return BB_EAST;
            case NORTH:
            default:    return BB_NORTH;
        }
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
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        return side.getAxis().isHorizontal();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }
}
