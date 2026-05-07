package com.micatechnologies.minecraft.sum.atm;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Drive-thru ATM. Same full-block footprint as the kiosk but visually distinct - the front
 * face shows a prominent receipt slot up top (at car-window height) with the keypad shifted
 * lower for a driver in the seat to reach. Bounding box keeps a 1-pixel inset on the front
 * so the screen surface visibly recedes a touch from a flush wall placement.
 */
public class BlockAtmDriveThru extends BlockAtmBase {

    private static final AxisAlignedBB BB = new AxisAlignedBB(
        0.0, 0.0, 1.0 / 16.0,
        1.0, 1.0, 1.0);

    public BlockAtmDriveThru() {
        super("atm_drive_thru");
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
