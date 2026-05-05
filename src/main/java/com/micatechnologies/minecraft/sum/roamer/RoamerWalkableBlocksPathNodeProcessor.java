package com.micatechnologies.minecraft.sum.roamer;

import com.micatechnologies.minecraft.sum.SumConfig;
import net.minecraft.block.Block;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.pathfinding.WalkNodeProcessor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Custom path node processor that restricts pathfinding to only blocks listed
 * in the SUM config's walkable blocks list. During emergency mode (fire/storm),
 * the restriction is bypassed so the roamer can navigate through buildings on
 * any solid block.
 */
public class RoamerWalkableBlocksPathNodeProcessor extends WalkNodeProcessor {

    // Reused across calls to avoid per-node BlockPos allocation. Safe because
    // WalkNodeProcessor.getPathNodeType(IBlockAccess, x, y, z) does not recurse.
    private final BlockPos.MutableBlockPos belowPos = new BlockPos.MutableBlockPos();

    @Override
    public PathNodeType getPathNodeType(IBlockAccess world, int x, int y, int z) {
        PathNodeType baseType = super.getPathNodeType(world, x, y, z);

        if (baseType == PathNodeType.WALKABLE) {
            if (this.entity instanceof EntityRoamer && ((EntityRoamer) this.entity).isEmergencyMode()) {
                return baseType;
            }

            belowPos.setPos(x, y - 1, z);
            Block below = world.getBlockState(belowPos).getBlock();
            if (!SumConfig.isBlockWalkableByRoamer(below)) {
                return PathNodeType.BLOCKED;
            }
        }

        return baseType;
    }
}
