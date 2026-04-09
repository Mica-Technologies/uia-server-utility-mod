package com.micatechnologies.minecraft.sum.roamer;

import com.micatechnologies.minecraft.sum.SumConfig;
import net.minecraft.block.state.IBlockState;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.pathfinding.WalkNodeProcessor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Custom path node processor that restricts pathfinding to only blocks listed
 * in the SUM config's walkable blocks list.
 */
public class RoamerWalkableBlocksPathNodeProcessor extends WalkNodeProcessor {

    @Override
    public PathNodeType getPathNodeType(IBlockAccess world, int x, int y, int z) {
        PathNodeType baseType = super.getPathNodeType(world, x, y, z);

        // Only allow walkable nodes if the block below is in the allowed set
        if (baseType == PathNodeType.WALKABLE) {
            BlockPos belowPos = new BlockPos(x, y - 1, z);
            IBlockState belowState = world.getBlockState(belowPos);
            String registryName = belowState.getBlock().getRegistryName().toString();
            if (!SumConfig.isBlockWalkableByRoamer(registryName)) {
                return PathNodeType.BLOCKED;
            }
        }

        return baseType;
    }
}
