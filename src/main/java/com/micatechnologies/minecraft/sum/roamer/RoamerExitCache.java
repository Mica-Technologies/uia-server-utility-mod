package com.micatechnologies.minecraft.sum.roamer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
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
 * Entries expire after {@link #EXPIRY_TICKS} to avoid stale data after alarms stop.
 * Cleanup is lazy — performed during lookups, not on a timer.
 */
public class RoamerExitCache {

    private static final int EXPIRY_TICKS = 6000; // 5 minutes
    private static final double MAX_DISTANCE_SQ = 50.0 * 50.0; // 50-block search radius

    // Exits within this Y difference from the roamer are considered "same level"
    private static final int SAME_LEVEL_TOLERANCE = 2;

    private static final List<CachedExit> exits = new ArrayList<>();

    /**
     * Records a known exit position. Called when a roamer transitions from indoors to outdoors.
     */
    public static void recordExit(BlockPos pos, long worldTime) {
        // Don't duplicate exits that are very close to existing ones
        for (CachedExit existing : exits) {
            if (existing.pos.distanceSq(pos) < 4.0) { // within 2 blocks
                existing.timestamp = worldTime; // refresh the timestamp
                return;
            }
        }
        exits.add(new CachedExit(pos, worldTime));
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
     * Lazily cleans up expired entries during the search.
     */
    public static List<BlockPos> findExitsByPriority(BlockPos from, long worldTime) {
        List<BlockPos> sameLevel = new ArrayList<>();
        List<BlockPos> otherLevel = new ArrayList<>();

        Iterator<CachedExit> it = exits.iterator();
        while (it.hasNext()) {
            CachedExit exit = it.next();
            if (worldTime - exit.timestamp > EXPIRY_TICKS) {
                it.remove();
                continue;
            }
            double distSq = from.distanceSq(exit.pos);
            if (distSq > MAX_DISTANCE_SQ) {
                continue;
            }

            int yDiff = Math.abs(from.getY() - exit.pos.getY());
            if (yDiff <= SAME_LEVEL_TOLERANCE) {
                sameLevel.add(exit.pos);
            } else {
                otherLevel.add(exit.pos);
            }
        }

        // Sort same-level by horizontal distance (ignore Y for ranking)
        sameLevel.sort(Comparator.comparingDouble(pos -> horizontalDistSq(from, pos)));
        // Sort other-level by full 3D distance
        otherLevel.sort(Comparator.comparingDouble(from::distanceSq));

        // Combine: same-level first, then other-level
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

    private static class CachedExit {
        final BlockPos pos;
        long timestamp;

        CachedExit(BlockPos pos, long timestamp) {
            this.pos = pos;
            this.timestamp = timestamp;
        }
    }
}
