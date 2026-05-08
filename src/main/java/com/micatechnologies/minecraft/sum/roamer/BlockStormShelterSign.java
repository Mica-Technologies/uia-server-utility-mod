package com.micatechnologies.minecraft.sum.roamer;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Storm-shelter sign: a thin wall-mounted plaque that registers its position into
 * {@link RoamerShelterCache} so roamers responding to a storm alarm consider it as a shelter
 * candidate without first having to organically discover the spot. Position registration
 * happens both on initial placement and on chunk reload (via the TE's {@code onLoad}), so
 * existing signs survive server restarts.
 *
 * <p>Wall-mounted: 2/16 thick, full 16×16 face on the wall, four horizontal facings. Use
 * {@code FACING} property to track orientation. The block hangs off the wall the player
 * clicked when placing — the placement face becomes the sign's facing direction.
 *
 * <p>Closes the loop in the existing storm-shelter system: admins put a sign in a known-good
 * spot, roamers seek it more reliably than they'd find it by random search.
 */
public class BlockStormShelterSign extends Block implements ITileEntityProvider {

    public static final PropertyDirection FACING = PropertyDirection.create(
        "facing", EnumFacing.Plane.HORIZONTAL);

    /** Body sits on the wall the sign is mounted against. For facing=NORTH, the sign faces
     *  north, so it's mounted on the south wall of its own block — body fills z=14..16. */
    private static final AxisAlignedBB BB_NORTH = new AxisAlignedBB(0.0, 0.0, 14.0 / 16.0, 1.0, 1.0, 1.0);
    private static final AxisAlignedBB BB_SOUTH = new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0, 2.0 / 16.0);
    private static final AxisAlignedBB BB_WEST = new AxisAlignedBB(14.0 / 16.0, 0.0, 0.0, 1.0, 1.0, 1.0);
    private static final AxisAlignedBB BB_EAST = new AxisAlignedBB(0.0, 0.0, 0.0, 2.0 / 16.0, 1.0, 1.0);

    public BlockStormShelterSign() {
        super(Material.IRON);
        setRegistryName(SumConstants.MOD_NAMESPACE, "storm_shelter_sign");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".storm_shelter_sign");
        setHardness(0.8F);
        setResistance(5.0F);
        setSoundType(SoundType.METAL);
        setCreativeTab(SumTab.TAB);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));

        SumRegistry.registerBlock(this);
        ItemBlock itemBlock = new ItemBlock(this);
        itemBlock.setRegistryName(getRegistryName());
        SumRegistry.registerItem(itemBlock);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityStormShelterSign();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        // The "facing" parameter is the side of the existing block the player clicked.
        // The new sign's facing is that direction (i.e. it points outward from the wall).
        if (facing.getAxis().isHorizontal()) {
            return getDefaultState().withProperty(FACING, facing);
        }
        // Vertical click (top/bottom) — fall back to the player's facing so the sign at
        // least points somewhere sensible instead of crashing.
        return getDefaultState().withProperty(FACING,
            placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote) {
            RoamerShelterCache.recordSignedShelter(pos);
        }
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
    public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public boolean isOpaqueCube(IBlockState state) { return false; }

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
