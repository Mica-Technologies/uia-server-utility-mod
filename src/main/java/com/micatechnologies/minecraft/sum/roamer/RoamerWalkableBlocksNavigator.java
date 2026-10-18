package com.micatechnologies.minecraft.sum.roamer;

import net.minecraft.entity.EntityLiving;
import net.minecraft.pathfinding.PathFinder;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.world.World;

/**
 * Custom ground navigator that uses the walkable-blocks-only path node processor
 * to restrict the roamer's movement to configured blocks.
 */
public class RoamerWalkableBlocksNavigator extends PathNavigateGround {

    public RoamerWalkableBlocksNavigator(EntityLiving entity, World world) {
        super(entity, world);
    }

    /** Search range outside an emergency: wander targets are at most 10 blocks away. */
    static final float NORMAL_SEARCH_RANGE = 20.0F;

    /**
     * The roamer's FOLLOW_RANGE (48) is sized for evacuating a building, but it also sets the
     * area every path search caches: {@code getPathToPos} builds a {@code ChunkCache} of range
     * + 8 blocks around the roamer, which loads chunks on the server. Paying for a 56-block
     * cache on every ordinary 10-block wander is most of a wander's cost, so the full range
     * is kept for emergencies only.
     */
    @Override
    public float getPathSearchRange() {
        float full = super.getPathSearchRange();
        if (entity instanceof EntityRoamer && ((EntityRoamer) entity).isEmergencyMode()) {
            return full;
        }
        return Math.min(full, NORMAL_SEARCH_RANGE);
    }

    @Override
    protected PathFinder getPathFinder() {
        this.nodeProcessor = new RoamerWalkableBlocksPathNodeProcessor();
        this.nodeProcessor.setCanEnterDoors(true);
        return new PathFinder(this.nodeProcessor);
    }
}
