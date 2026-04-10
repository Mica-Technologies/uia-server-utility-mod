package com.micatechnologies.minecraft.sum.roamer;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

/**
 * AI task that makes the roamer evacuate to the outside when a CSM fire alarm is active nearby.
 * The roamer searches for a walkable outdoor position (one that can see the sky and has a
 * sufficiently large open area to avoid balconies) and pathfinds to it.
 * <p>
 * This task only activates when CSM is loaded and a fire alarm (not storm) is sounding within
 * hearing range. If the roamer is already outside, the task does nothing.
 */
public class EntityAIRoamerFireEvacuate extends EntityAIBase {

  private static final int CHECK_INTERVAL_TICKS = 40;
  private static final int SEARCH_RADIUS_XZ = 30;
  private static final int SEARCH_RADIUS_Y = 10;
  private static final int MIN_OPEN_SKY_BLOCKS = 60;
  private static final int OPEN_AREA_CHECK_RADIUS = 5;
  private static final int REPATH_INTERVAL_TICKS = 60;

  private final EntityCreature entity;
  private final double speed;
  private final boolean csmLoaded;

  private BlockPos exitTarget;
  private int checkTimer;
  private int repathTimer;

  public EntityAIRoamerFireEvacuate(EntityCreature entity, double speed) {
    this.entity = entity;
    this.speed = speed;
    this.csmLoaded = Loader.isModLoaded("csm");
    this.setMutexBits(1); // movement mutex - conflicts with wander
  }

  @Override
  public boolean shouldExecute() {
    if (!csmLoaded) {
      return false;
    }

    // Throttle the alarm check to avoid per-tick overhead
    if (checkTimer > 0) {
      checkTimer--;
      return false;
    }
    checkTimer = CHECK_INTERVAL_TICKS;

    World world = entity.world;
    BlockPos entityPos = entity.getPosition();

    // Don't evacuate if already outside
    if (isOutdoorPosition(world, entityPos)) {
      return false;
    }

    // Check if fire alarm is active nearby (not storm - storm requires shelter, not evacuation)
    if (!CsmIntegration.isFireAlarmActiveNear(world, entityPos)) {
      return false;
    }

    // Find an exit
    exitTarget = findExitPosition(world, entityPos);
    return exitTarget != null;
  }

  @Override
  public boolean shouldContinueExecuting() {
    if (!csmLoaded) {
      return false;
    }

    // Stop if the alarm has been silenced/reset
    if (!CsmIntegration.isFireAlarmActiveNear(entity.world, entity.getPosition())) {
      return false;
    }

    // Stop if we've reached outside
    if (isOutdoorPosition(entity.world, entity.getPosition())) {
      return false;
    }

    // Stop if navigation is completely done (path exhausted) - we either arrived or got stuck
    return !entity.getNavigator().noPath();
  }

  @Override
  public void startExecuting() {
    repathTimer = 0;
    entity.getNavigator().tryMoveToXYZ(exitTarget.getX() + 0.5, exitTarget.getY(),
        exitTarget.getZ() + 0.5, speed);
  }

  @Override
  public void updateTask() {
    // Periodically re-path in case of obstacles
    repathTimer++;
    if (repathTimer >= REPATH_INTERVAL_TICKS) {
      repathTimer = 0;
      if (exitTarget != null) {
        entity.getNavigator().tryMoveToXYZ(exitTarget.getX() + 0.5, exitTarget.getY(),
            exitTarget.getZ() + 0.5, speed);
      }
    }
  }

  @Override
  public void resetTask() {
    exitTarget = null;
    checkTimer = CHECK_INTERVAL_TICKS;
  }

  /**
   * Checks if a position is outdoors by verifying sky visibility and a large enough open area
   * around it (to exclude balconies, courtyards, etc.).
   */
  private boolean isOutdoorPosition(World world, BlockPos pos) {
    if (!world.canSeeSky(pos.up())) {
      return false;
    }
    return countOpenSkyBlocks(world, pos, OPEN_AREA_CHECK_RADIUS) >= MIN_OPEN_SKY_BLOCKS;
  }

  /**
   * Counts the number of blocks within a horizontal radius that can see the sky.
   */
  private int countOpenSkyBlocks(World world, BlockPos center, int radius) {
    int count = 0;
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        if (world.canSeeSky(center.add(dx, 1, dz))) {
          count++;
        }
      }
    }
    return count;
  }

  /**
   * Searches outward from the entity's position for a walkable outdoor position that qualifies
   * as a valid exit (large open sky area, not just a balcony).
   */
  private BlockPos findExitPosition(World world, BlockPos entityPos) {
    BlockPos bestExit = null;
    double bestDistSq = Double.MAX_VALUE;

    // Search in expanding rings from the entity for efficiency
    for (int r = 1; r <= SEARCH_RADIUS_XZ; r++) {
      for (int dx = -r; dx <= r; dx++) {
        for (int dz = -r; dz <= r; dz++) {
          // Only check the outer ring of each radius to avoid re-checking
          if (Math.abs(dx) != r && Math.abs(dz) != r) {
            continue;
          }

          for (int dy = -SEARCH_RADIUS_Y; dy <= SEARCH_RADIUS_Y; dy++) {
            BlockPos candidate = entityPos.add(dx, dy, dz);

            if (!world.isBlockLoaded(candidate)) {
              continue;
            }

            // Must be a walkable position (air with solid ground below)
            if (!world.isAirBlock(candidate) || !world.isAirBlock(candidate.up())) {
              continue;
            }
            if (!world.getBlockState(candidate.down()).getMaterial().isSolid()) {
              continue;
            }

            // Must be able to see sky
            if (!world.canSeeSky(candidate.up())) {
              continue;
            }

            // Must have a large open area (not a balcony)
            if (countOpenSkyBlocks(world, candidate, OPEN_AREA_CHECK_RADIUS) < MIN_OPEN_SKY_BLOCKS) {
              continue;
            }

            double distSq = entityPos.distanceSq(candidate);
            if (distSq < bestDistSq) {
              bestDistSq = distSq;
              bestExit = candidate;
            }
          }
        }
      }

      // If we found an exit at this radius, no need to search further
      if (bestExit != null) {
        return bestExit;
      }
    }

    return bestExit;
  }
}
