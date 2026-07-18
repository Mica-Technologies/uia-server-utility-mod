package com.micatechnologies.minecraft.sum.roamer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the static {@link RoamerExitCache}: 2-block dedup on record, the 100-block range cutoff,
 * and the same-level-before-other-level priority ordering. The cache is static process state, so
 * each test clears it before and after.
 */
class RoamerExitCacheTest {

    @BeforeEach
    @AfterEach
    void reset() {
        RoamerExitCache.clear();
    }

    @Test
    void recordsAndFindsAnExit() {
        RoamerExitCache.recordExit(new BlockPos(5, 64, 5));
        assertEquals(1, RoamerExitCache.findExitsByPriority(new BlockPos(0, 64, 0)).size());
    }

    @Test
    void suppressesNearDuplicates() {
        RoamerExitCache.recordExit(new BlockPos(0, 64, 0));
        RoamerExitCache.recordExit(new BlockPos(1, 64, 0)); // distSq 1 < 4 → suppressed
        assertEquals(1, RoamerExitCache.findExitsByPriority(new BlockPos(0, 64, 0)).size());
    }

    @Test
    void keepsExitsAtOrBeyondTheDedupRadius() {
        RoamerExitCache.recordExit(new BlockPos(0, 64, 0));
        RoamerExitCache.recordExit(new BlockPos(0, 64, 3)); // distSq 9 ≥ 4 → kept
        assertEquals(2, RoamerExitCache.findExitsByPriority(new BlockPos(0, 64, 0)).size());
    }

    @Test
    void excludesExitsBeyondMaxRange() {
        RoamerExitCache.recordExit(new BlockPos(200, 64, 0)); // >100 blocks away
        assertTrue(RoamerExitCache.findExitsByPriority(new BlockPos(0, 64, 0)).isEmpty());
    }

    @Test
    void sameLevelExitsSortBeforeOtherLevelDespiteDistance() {
        BlockPos from = new BlockPos(0, 64, 0);
        BlockPos sameLevelFar = new BlockPos(50, 66, 0);  // yDiff 2 → same level, far
        BlockPos otherLevelNear = new BlockPos(5, 67, 0); // yDiff 3 → other level, near
        RoamerExitCache.recordExit(otherLevelNear);
        RoamerExitCache.recordExit(sameLevelFar);

        List<BlockPos> ordered = RoamerExitCache.findExitsByPriority(from);
        assertEquals(sameLevelFar, ordered.get(0),
            "a same-level (yDiff≤2) exit outranks a closer other-level exit");
        assertEquals(otherLevelNear, ordered.get(1));
    }

    @Test
    void clearEmptiesTheCache() {
        RoamerExitCache.recordExit(new BlockPos(5, 64, 5));
        RoamerExitCache.clear();
        assertTrue(RoamerExitCache.findExitsByPriority(new BlockPos(0, 64, 0)).isEmpty());
    }
}
