package com.micatechnologies.minecraft.sum.plots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Tests the chunk→plot index that backs O(1) protection lookups ({@link SumPlotsChunkIndex}).
 * Verifies a plot is registered into every chunk its bbox touches, lookups resolve by block
 * position, and removal cleans up empty chunk buckets.
 */
class SumPlotsChunkIndexTest {

    private static SumPlot plot(UUID id, BlockPos a, BlockPos b) {
        return new SumPlot(id, "p", a, b, 0, 0.0, PlotStatus.OWNED, 0L);
    }

    @Test
    void singleChunkPlotRegistersInOneChunk() {
        SumPlotsChunkIndex index = new SumPlotsChunkIndex();
        UUID id = UUID.randomUUID();
        index.addPlot(plot(id, new BlockPos(2, 0, 2), new BlockPos(14, 10, 14)));

        assertTrue(index.plotIdsAtChunk(0, 0).contains(id));
        assertNull(index.plotIdsAtChunk(1, 0), "neighbor chunk should have no entry");
    }

    @Test
    void multiChunkPlotRegistersInEveryTouchedChunk() {
        SumPlotsChunkIndex index = new SumPlotsChunkIndex();
        UUID id = UUID.randomUUID();
        // 0..31 spans chunk x{0,1}; 0..31 spans chunk z{0,1} → 4 chunks.
        index.addPlot(plot(id, new BlockPos(0, 0, 0), new BlockPos(31, 255, 31)));

        assertTrue(index.plotIdsAtChunk(0, 0).contains(id));
        assertTrue(index.plotIdsAtChunk(0, 1).contains(id));
        assertTrue(index.plotIdsAtChunk(1, 0).contains(id));
        assertTrue(index.plotIdsAtChunk(1, 1).contains(id));
        assertNull(index.plotIdsAtChunk(2, 0), "chunk just outside the bbox is empty");
    }

    @Test
    void plotIdsAtResolvesBlockPositionToChunk() {
        SumPlotsChunkIndex index = new SumPlotsChunkIndex();
        UUID id = UUID.randomUUID();
        index.addPlot(plot(id, new BlockPos(0, 0, 0), new BlockPos(15, 10, 15)));

        List<UUID> hit = index.plotIdsAt(new BlockPos(8, 5, 8));
        assertTrue(hit.contains(id));
        // (20,20) is in chunk (1,1) — outside the plot's single chunk.
        assertTrue(index.plotIdsAt(new BlockPos(20, 5, 20)).isEmpty());
    }

    @Test
    void handlesNegativeChunkCoordinates() {
        SumPlotsChunkIndex index = new SumPlotsChunkIndex();
        UUID id = UUID.randomUUID();
        index.addPlot(plot(id, new BlockPos(-20, 0, -20), new BlockPos(-5, 10, -5)));

        // -20 >> 4 == -2 ; -5 >> 4 == -1
        assertTrue(index.plotIdsAtChunk(-2, -2).contains(id));
        assertTrue(index.plotIdsAtChunk(-1, -1).contains(id));
        assertTrue(index.plotIdsAt(new BlockPos(-10, 5, -10)).contains(id));
    }

    @Test
    void overlappingPlotsBothRegisterInSharedChunk() {
        SumPlotsChunkIndex index = new SumPlotsChunkIndex();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        index.addPlot(plot(a, new BlockPos(0, 0, 0), new BlockPos(10, 10, 10)));
        index.addPlot(plot(b, new BlockPos(5, 20, 5), new BlockPos(12, 30, 12)));

        List<UUID> hits = index.plotIdsAtChunk(0, 0);
        assertTrue(hits.contains(a));
        assertTrue(hits.contains(b));
        assertEquals(2, hits.size());
    }

    @Test
    void removePlotClearsEmptyBuckets() {
        SumPlotsChunkIndex index = new SumPlotsChunkIndex();
        SumPlot p = plot(UUID.randomUUID(), new BlockPos(0, 0, 0), new BlockPos(31, 10, 31));
        index.addPlot(p);
        index.removePlot(p);

        assertNull(index.plotIdsAtChunk(0, 0));
        assertNull(index.plotIdsAtChunk(1, 1));
        assertTrue(index.plotIdsAt(new BlockPos(8, 5, 8)).isEmpty());
    }

    @Test
    void removeLeavesCoResidentPlotIntact() {
        SumPlotsChunkIndex index = new SumPlotsChunkIndex();
        SumPlot a = plot(UUID.randomUUID(), new BlockPos(0, 0, 0), new BlockPos(10, 10, 10));
        SumPlot b = plot(UUID.randomUUID(), new BlockPos(2, 0, 2), new BlockPos(8, 10, 8));
        index.addPlot(a);
        index.addPlot(b);
        index.removePlot(a);

        List<UUID> hits = index.plotIdsAtChunk(0, 0);
        assertEquals(1, hits.size());
        assertTrue(hits.contains(b.getPlotId()));
    }

    @Test
    void rebuildReplacesContents() {
        SumPlotsChunkIndex index = new SumPlotsChunkIndex();
        SumPlot stale = plot(UUID.randomUUID(), new BlockPos(0, 0, 0), new BlockPos(5, 5, 5));
        index.addPlot(stale);

        SumPlot fresh = plot(UUID.randomUUID(), new BlockPos(100, 0, 100), new BlockPos(110, 5, 110));
        index.rebuild(java.util.Collections.singletonList(fresh));

        assertNull(index.plotIdsAtChunk(0, 0), "stale plot's chunk should be gone after rebuild");
        assertTrue(index.plotIdsAtChunk(6, 6).contains(fresh.getPlotId()));
    }

    @Test
    void clearEmptiesIndex() {
        SumPlotsChunkIndex index = new SumPlotsChunkIndex();
        index.addPlot(plot(UUID.randomUUID(), new BlockPos(0, 0, 0), new BlockPos(5, 5, 5)));
        index.clear();
        assertNull(index.plotIdsAtChunk(0, 0));
    }
}
