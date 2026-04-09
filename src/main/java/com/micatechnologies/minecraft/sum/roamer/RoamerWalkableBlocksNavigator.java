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

    @Override
    protected PathFinder getPathFinder() {
        this.nodeProcessor = new RoamerWalkableBlocksPathNodeProcessor();
        this.nodeProcessor.setCanEnterDoors(true);
        return new PathFinder(this.nodeProcessor);
    }
}
