package com.micatechnologies.minecraft.sum.roamer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.math.BlockPos;

/**
 * Shared static cache of confirmed shelter positions discovered by roamers during storm shelter
 * AI. When a roamer reaches a position that passed the full hazard/sky checks, it records the
 * position here. Subsequent roamers responding to a later storm can try cached positions first,
 * skipping the O(R^3) cube scan when the building's shelter zone is already known.
 * <p>
 * Within a single storm wave, cached positions are usually claimed by other roamers and the
 * lookup falls through to the full search; the win shows up on the second and later waves, when
 * claims have been released and the geometry of the building hasn't changed.
 * <p>
 * Entries persist for the lifetime of the world. Stale entries (e.g. building demolished) simply
 * fail subsequent isShelterPosition or pathfinder checks and are filtered out at lookup time.
 */
public class RoamerShelterCache {

    private static final double MAX_DISTANCE_SQ = 100.0 * 100.0;

    private static final List<BlockPos> shelters = new ArrayList<>();

    /**
     * Records a confirmed shelter position. Duplicate positions within ~3 blocks of an existing
     * entry are suppressed so the cache stays compact even if many roamers shelter in one room.
     */
    public static void recordShelter(BlockPos pos) {
        for (BlockPos existing : shelters) {
            if (existing.distanceSq(pos) < 9.0) {
                return;
            }
        }
        shelters.add(pos);
    }

    /**
     * Returns cached shelter positions within range of {@code from}, sorted by 3D distance.
     * Caller is expected to filter for current claimedness, hazard validity, and reachability.
     */
    public static List<BlockPos> findNearest(BlockPos from) {
        List<BlockPos> nearby = new ArrayList<>();
        for (BlockPos shelter : shelters) {
            if (from.distanceSq(shelter) <= MAX_DISTANCE_SQ) {
                nearby.add(shelter);
            }
        }
        nearby.sort(Comparator.comparingDouble(from::distanceSq));
        return nearby;
    }

    public static void clear() {
        shelters.clear();
    }
}
