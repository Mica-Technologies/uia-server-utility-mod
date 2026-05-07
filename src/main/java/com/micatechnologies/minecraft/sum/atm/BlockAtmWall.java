package com.micatechnologies.minecraft.sum.atm;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Wall-mounted ATM. Smaller footprint than the kiosk - the unit only occupies a thin shell on
 * the side of its block facing the player, leaving the back attached to whatever wall stands
 * behind it.
 */
public class BlockAtmWall extends BlockAtmBase {

    private static final AxisAlignedBB BB_FACING_NORTH = new AxisAlignedBB(
        3.0 / 16.0, 0.0, 0.0,
        13.0 / 16.0, 1.0, 5.0 / 16.0);
    private static final AxisAlignedBB BB_FACING_SOUTH = new AxisAlignedBB(
        3.0 / 16.0, 0.0, 11.0 / 16.0,
        13.0 / 16.0, 1.0, 1.0);
    private static final AxisAlignedBB BB_FACING_WEST = new AxisAlignedBB(
        0.0, 0.0, 3.0 / 16.0,
        5.0 / 16.0, 1.0, 13.0 / 16.0);
    private static final AxisAlignedBB BB_FACING_EAST = new AxisAlignedBB(
        11.0 / 16.0, 0.0, 3.0 / 16.0,
        1.0, 1.0, 13.0 / 16.0);

    public BlockAtmWall() {
        super("atm_wall");
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        switch (state.getValue(FACING)) {
            case SOUTH: return BB_FACING_SOUTH;
            case WEST:  return BB_FACING_WEST;
            case EAST:  return BB_FACING_EAST;
            case NORTH:
            default:    return BB_FACING_NORTH;
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
    public boolean canPlaceBlockOnSide(net.minecraft.world.World world, BlockPos pos, EnumFacing side) {
        // Only place when the user clicks the face of an existing block - the resulting wall
        // ATM ends up flush with that face.
        return side.getAxis().isHorizontal();
    }
}
