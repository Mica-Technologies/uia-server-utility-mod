package com.micatechnologies.minecraft.sum.jail;

import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Opens and closes the doors linked to a jail cell.
 *
 * <p>Anything with a boolean {@code open} property counts as a door: vanilla and modded doors,
 * trapdoors, fence gates, and CSM's Holding Cell Door. Going by the property rather than by class
 * means SUM needs no compile-time dependency on the CSM version that added the cell door.
 *
 * <p>A vanilla door keeps its state on its lower half, so it goes through
 * {@link BlockDoor#toggleDoor}. Anything else is set directly, together with every block of the
 * same kind stacked above and below it, because a CSM cell door is two one-block doors stacked and
 * opens as one.
 */
final class JailDoors {

    private JailDoors() {}

    static boolean isDoor(IBlockState state) {
        return openProperty(state) != null;
    }

    /** Opens or closes the door at {@code pos}. Returns false if there is no door there now. */
    static boolean setOpen(World world, BlockPos pos, boolean open) {
        if (!world.isBlockLoaded(pos)) {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof BlockDoor) {
            ((BlockDoor) block).toggleDoor(world, pos, open);
            return true;
        }
        PropertyBool property = openProperty(state);
        if (property == null) {
            return false;
        }
        BlockPos bottom = pos;
        while (world.getBlockState(bottom.down()).getBlock() == block) {
            bottom = bottom.down();
        }
        boolean changed = false;
        for (BlockPos p = bottom; world.getBlockState(p).getBlock() == block; p = p.up()) {
            IBlockState s = world.getBlockState(p);
            if (s.getValue(property) != open) {
                world.setBlockState(p, s.withProperty(property, open), 3);
                changed = true;
            }
        }
        if (changed) {
            world.playSound(null, pos,
                open ? SoundEvents.BLOCK_IRON_DOOR_OPEN : SoundEvents.BLOCK_IRON_DOOR_CLOSE,
                SoundCategory.BLOCKS, 1.0F, 0.7F);
        }
        return true;
    }

    @Nullable
    private static PropertyBool openProperty(IBlockState state) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if ("open".equals(property.getName()) && property instanceof PropertyBool) {
                return (PropertyBool) property;
            }
        }
        return null;
    }
}
