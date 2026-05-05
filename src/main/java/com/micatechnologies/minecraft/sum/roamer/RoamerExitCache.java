package com.micatechnologies.minecraft.sum.roamer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.math.BlockPos;

/**
 * Shared static cache of known building exit positions discovered by roamers during fire
 * evacuation. When a roamer successfully exits a building, it records the doorway position here.
 * Other roamers still inside can check the cache for nearby known exits instead of running the
 * expensive area search.
 * <p>
 * Exits are returned prioritized by Y-level proximity (same floor first) then by horizontal
 * distance. This ensures roamers prefer exits they can reach without finding stairs, falling
 * back to other-floor exits only if same-level ones aren't reachable.
 * <p>
 * Entries persist until world unload — building geometry doesn't change with time, and a stale
 * entry that fails pathfinding falls through to a fresh search with no harm done.
 */
public class RoamerExitCache {

    // Search radius for nearby cached exits. Buildings on the Alto pack can be large; 100 blocks
    // covers most multi-building compounds while still rejecting unrelated exits across town.
    private static final double MAX_DISTANCE_SQ = 100.0 * 100.0;

    // Exits within this Y difference from the roamer are considered "same level"
    private static final int SAME_LEVEL_TOLERANCE = 2;

    private static final List<BlockPos> exits = new ArrayList<>();

    /**
     * Records a known exit position. Called when a roamer transitions from indoors to outdoors.
     * Duplicate exits within 2 blocks of an existing entry are suppressed.
     */
    public static void recordExit(BlockPos pos) {
        for (BlockPos existing : exits) {
            if (existing.distanceSq(pos) < 4.0) {
                return;
            }
        }
        exits.add(pos);
    }

    /**
     * Returns all cached exits within range of the given position, sorted by priority:
     * <ol>
     *   <li>Same-level exits (within {@link #SAME_LEVEL_TOLERANCE} Y) sorted by horizontal
     *       distance — these are reachable without stairs</li>
     *   <li>Other-level exits sorted by total 3D distance — fallbacks that require vertical
     *       navigation</li>
     * </ol>
     * The caller should try each in order with the pathfinder and use the first reachable one.
     */
    public static List<BlockPos> findExitsByPriority(BlockPos from) {
        List<BlockPos> sameLevel = new ArrayList<>();
        List<BlockPos> otherLevel = new ArrayList<>();

        for (BlockPos exit : exits) {
            double distSq = from.distanceSq(exit);
            if (distSq > MAX_DISTANCE_SQ) {
                continue;
            }

            int yDiff = Math.abs(from.getY() - exit.getY());
            if (yDiff <= SAME_LEVEL_TOLERANCE) {
                sameLevel.add(exit);
            } else {
                otherLevel.add(exit);
            }
        }

        sameLevel.sort(Comparator.comparingDouble(pos -> horizontalDistSq(from, pos)));
        otherLevel.sort(Comparator.comparingDouble(from::distanceSq));

        List<BlockPos> result = new ArrayList<>(sameLevel.size() + otherLevel.size());
        result.addAll(sameLevel);
        result.addAll(otherLevel);
        return result;
    }

    /**
     * Clears all cached exits. Called when the server stops or world unloads.
     */
    public static void clear() {
        exits.clear();
    }

    private static double horizontalDistSq(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
