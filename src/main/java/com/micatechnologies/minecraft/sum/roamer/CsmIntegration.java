package com.micatechnologies.minecraft.sum.roamer;

import com.micatechnologies.minecraft.csm.api.firealarm.CsmFireAlarmQuery;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Soft dependency wrapper for City Super Mod (CSM) fire alarm integration. This class must ONLY
 * be referenced from within a {@code Loader.isModLoaded("csm")} guard, as it directly imports
 * CSM API classes and will cause a {@code ClassNotFoundException} if CSM is not present.
 */
public class CsmIntegration {

  /**
   * Checks whether a fire alarm (non-storm) is actively sounding within hearing range of the
   * given position. Returns false if CSM is not loaded (caller should check first).
   */
  public static boolean isFireAlarmActiveNear(World world, BlockPos pos) {
    return CsmFireAlarmQuery.isFireAlarmActiveNear(world, pos);
  }

  /**
   * Checks whether a storm/tornado alarm is actively sounding within hearing range of the
   * given position. Returns false if CSM is not loaded (caller should check first).
   */
  public static boolean isStormAlarmActiveNear(World world, BlockPos pos) {
    return CsmFireAlarmQuery.isStormAlarmActiveNear(world, pos);
  }
}
