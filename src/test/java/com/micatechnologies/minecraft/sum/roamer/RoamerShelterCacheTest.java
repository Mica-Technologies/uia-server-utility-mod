package com.micatechnologies.minecraft.sum.roamer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the static {@link RoamerShelterCache}: 3-block dedup on the general cache, nearest-first
 * range-limited lookups, and the key divergence that the {@code signedShelters} set has NO
 * distance dedup (so two sign-marked positions a block apart both survive, while the general
 * cache would collapse them). Static state → cleared around each test.
 */
class RoamerShelterCacheTest {

    @BeforeEach
    @AfterEach
    void reset() {
        RoamerShelterCache.clear();
    }

    @Test
    void suppressesNearDuplicatesInGeneralCache() {
        RoamerShelterCache.recordShelter(new BlockPos(0, 64, 0));
        RoamerShelterCache.recordShelter(new BlockPos(0, 64, 2)); // distSq 4 < 9 → suppressed
        assertEquals(1, RoamerShelterCache.findNearest(new BlockPos(0, 64, 0)).size());
    }

    @Test
    void keepsSheltersAtOrBeyondDedupRadius() {
        RoamerShelterCache.recordShelter(new BlockPos(0, 64, 0));
        RoamerShelterCache.recordShelter(new BlockPos(0, 64, 3)); // distSq 9 ≥ 9 → kept
        assertEquals(2, RoamerShelterCache.findNearest(new BlockPos(0, 64, 0)).size());
    }

    @Test
    void excludesSheltersBeyondMaxRange() {
        RoamerShelterCache.recordShelter(new BlockPos(200, 64, 0));
        assertTrue(RoamerShelterCache.findNearest(new BlockPos(0, 64, 0)).isEmpty());
    }

    @Test
    void findNearestSortsByDistance() {
        BlockPos from = new BlockPos(0, 64, 0);
        RoamerShelterCache.recordShelter(new BlockPos(10, 64, 0));
        RoamerShelterCache.recordShelter(new BlockPos(5, 64, 0));
        RoamerShelterCache.recordShelter(new BlockPos(20, 64, 0));
        List<BlockPos> ordered = RoamerShelterCache.findNearest(from);
        assertEquals(new BlockPos(5, 64, 0), ordered.get(0));
        assertEquals(new BlockPos(10, 64, 0), ordered.get(1));
        assertEquals(new BlockPos(20, 64, 0), ordered.get(2));
    }

    @Test
    void signedShelterAppearsInBothLists() {
        BlockPos from = new BlockPos(0, 64, 0);
        RoamerShelterCache.recordSignedShelter(new BlockPos(3, 64, 0));
        assertEquals(1, RoamerShelterCache.findNearest(from).size());
        assertEquals(1, RoamerShelterCache.findNearestSigned(from).size());
    }

    @Test
    void signedListSkipsTheGeneralDistanceDedup() {
        BlockPos from = new BlockPos(0, 64, 0);
        RoamerShelterCache.recordSignedShelter(new BlockPos(0, 64, 0));
        RoamerShelterCache.recordSignedShelter(new BlockPos(1, 64, 0)); // 1 block apart
        // General cache dedups the second (distSq 1 < 9); the signed set keeps both.
        assertEquals(1, RoamerShelterCache.findNearest(from).size());
        assertEquals(2, RoamerShelterCache.findNearestSigned(from).size());
    }

    @Test
    void signedSetDedupsIdenticalPositions() {
        BlockPos from = new BlockPos(0, 64, 0);
        RoamerShelterCache.recordSignedShelter(new BlockPos(5, 64, 5));
        RoamerShelterCache.recordSignedShelter(new BlockPos(5, 64, 5)); // exact duplicate
        assertEquals(1, RoamerShelterCache.findNearestSigned(from).size());
    }

    @Test
    void clearEmptiesBothLists() {
        RoamerShelterCache.recordSignedShelter(new BlockPos(5, 64, 5));
        RoamerShelterCache.clear();
        BlockPos from = new BlockPos(0, 64, 0);
        assertTrue(RoamerShelterCache.findNearest(from).isEmpty());
        assertTrue(RoamerShelterCache.findNearestSigned(from).isEmpty());
    }
}
