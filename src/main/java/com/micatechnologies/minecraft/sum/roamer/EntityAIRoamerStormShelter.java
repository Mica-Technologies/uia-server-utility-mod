package com.micatechnologies.minecraft.sum.roamer;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

/**
 * AI task that makes the roamer seek shelter during a CSM storm/tornado alarm. Unlike fire
 * evacuation, during a storm the roamer should go INSIDE and to the lowest accessible floor,
 * away from windows (air blocks adjacent to sky-visible positions).
 * <p>
 * This task only activates when CSM is loaded and a storm alarm is sounding within hearing range.
 */
public class EntityAIRoamerStormShelter extends EntityAIBase {

  private static final int CHECK_INTERVAL_TICKS = 40;
  private static final int SEARCH_RADIUS_XZ = 25;
  private static final int SEARCH_RADIUS_Y_DOWN = 20;
  private static final int SEARCH_RADIUS_Y_UP = 5;
  private static final int WINDOW_SAFETY_DISTANCE = 3;
  private static final int REPATH_INTERVAL_TICKS = 60;

  private final EntityCreature entity;
  private final double speed;
  private final boolean csmLoaded;

  private BlockPos shelterTarget;
  private int checkTimer;
  private int repathTimer;

  public EntityAIRoamerStormShelter(EntityCreature entity, double speed) {
    this.entity = entity;
    this.speed = speed;
    this.csmLoaded = Loader.isModLoaded("csm");
    this.setMutexBits(1); // movement mutex
  }

  @Override
  public boolean shouldExecute() {
    if (!csmLoaded) {
      return false;
    }

    if (checkTimer > 0) {
      checkTimer--;
      return false;
    }
    checkTimer = CHECK_INTERVAL_TICKS;

    World world = entity.world;
    BlockPos entityPos = entity.getPosition();

    if (!CsmIntegration.isStormAlarmActiveNear(world, entityPos)) {
      return false;
    }

    // If already in a good shelter position, don't move
    if (isShelterPosition(world, entityPos)) {
      return false;
    }

    shelterTarget = findShelterPosition(world, entityPos);
    return shelterTarget != null;
  }

  @Override
  public boolean shouldContinueExecuting() {
    if (!csmLoaded) {
      return false;
    }

    // Stop if storm alarm ended
    if (!CsmIntegration.isStormAlarmActiveNear(entity.world, entity.getPosition())) {
      return false;
    }

    // Stop if we've reached a good shelter
    if (isShelterPosition(entity.world, entity.getPosition())) {
      return false;
    }

    return !entity.getNavigator().noPath();
  }

  @Override
  public void startExecuting() {
    repathTimer = 0;
    entity.getNavigator().tryMoveToXYZ(shelterTarget.getX() + 0.5, shelterTarget.getY(),
        shelterTarget.getZ() + 0.5, speed);
  }

  @Override
  public void updateTask() {
    repathTimer++;
    if (repathTimer >= REPATH_INTERVAL_TICKS) {
      repathTimer = 0;
      if (shelterTarget != null) {
        entity.getNavigator().tryMoveToXYZ(shelterTarget.getX() + 0.5, shelterTarget.getY(),
            shelterTarget.getZ() + 0.5, speed);
      }
    }
  }

  @Override
  public void resetTask() {
    shelterTarget = null;
    checkTimer = CHECK_INTERVAL_TICKS;
  }

  /**
   * A position is a good storm shelter if it cannot see the sky and is away from any nearby
   * sky-visible positions (windows).
   */
  private boolean isShelterPosition(World world, BlockPos pos) {
    if (world.canSeeSky(pos.up())) {
      return false;
    }
    return !isNearWindow(world, pos);
  }

  /**
   * Checks whether the given position is within {@link #WINDOW_SAFETY_DISTANCE} blocks of a
   * position that can see the sky (indicating a window or opening).
   */
  private boolean isNearWindow(World world, BlockPos pos) {
    int r = WINDOW_SAFETY_DISTANCE;
    for (int dx = -r; dx <= r; dx++) {
      for (int dz = -r; dz <= r; dz++) {
        for (int dy = -1; dy <= 2; dy++) {
          BlockPos check = pos.add(dx, dy, dz);
          if (world.isAirBlock(check) && world.canSeeSky(check)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Searches for the best storm shelter position: lowest floor, indoors, away from windows.
   * Prioritizes lower Y positions (basements, ground floors) and positions far from sky exposure.
   */
  private BlockPos findShelterPosition(World world, BlockPos entityPos) {
    BlockPos bestShelter = null;
    int bestScore = Integer.MIN_VALUE;

    for (int dy = -SEARCH_RADIUS_Y_DOWN; dy <= SEARCH_RADIUS_Y_UP; dy++) {
      for (int dx = -SEARCH_RADIUS_XZ; dx <= SEARCH_RADIUS_XZ; dx++) {
        for (int dz = -SEARCH_RADIUS_XZ; dz <= SEARCH_RADIUS_XZ; dz++) {
          BlockPos candidate = entityPos.add(dx, dy, dz);

          if (!world.isBlockLoaded(candidate)) {
            continue;
          }

          // Must be a walkable position
          if (!world.isAirBlock(candidate) || !world.isAirBlock(candidate.up())) {
            continue;
          }
          if (!world.getBlockState(candidate.down()).getMaterial().isSolid()) {
            continue;
          }

          // Must NOT see sky
          if (world.canSeeSky(candidate.up())) {
            continue;
          }

          // Must be away from windows
          if (isNearWindow(world, candidate)) {
            continue;
          }

          // Score: prefer lower Y and closer horizontal distance
          int yScore = (entityPos.getY() - candidate.getY()) * 10; // strongly prefer lower
          int distScore = -(Math.abs(dx) + Math.abs(dz)); // slightly prefer closer
          int score = yScore + distScore;

          if (score > bestScore) {
            bestScore = score;
            bestShelter = candidate;
          }
        }
      }
    }

    return bestShelter;
  }
}
