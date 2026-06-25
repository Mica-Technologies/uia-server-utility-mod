package com.micatechnologies.minecraft.sum.plots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Tests the per-dimension plot store ({@link SumPlotsWorldSavedData}) at the unit level:
 * overlap refusal (TESTING_PLAN.md §4.4 D2 — "create with overlapping selection refused"),
 * per-dimension filtering, FOR_SALE listing, ownership queries, and nearest-plot search.
 *
 * <p>Constructed directly via its public no-arg constructor — {@code WorldSavedData} only
 * stores a name and {@code markDirty()} just flips a boolean, so no live world is needed.
 */
class SumPlotsWorldSavedDataTest {

    private static SumPlot plot(BlockPos a, BlockPos b, int dim, PlotStatus status) {
        return new SumPlot(UUID.randomUUID(), "p", a, b, dim, 100.0, status, 0L);
    }

    @Test
    void overlapDetectedForIntersectingBox() {
        SumPlotsWorldSavedData data = new SumPlotsWorldSavedData();
        data.addPlot(plot(new BlockPos(0, 0, 0), new BlockPos(10, 255, 10), 0, PlotStatus.OWNED));

        assertTrue(data.overlapsAny(new BlockPos(5, 0, 5), new BlockPos(15, 255, 15), 0),
            "a box that intersects an existing plot must be reported as overlapping");
    }

    @Test
    void touchingFaceCountsAsOverlap() {
        // Existing 0..10; candidate 10..20 shares the x=10 plane. The handler's inclusive
        // bounds treat that shared block column as overlap (refuses adjacency-by-one-block).
        SumPlotsWorldSavedData data = new SumPlotsWorldSavedData();
        data.addPlot(plot(new BlockPos(0, 0, 0), new BlockPos(10, 255, 10), 0, PlotStatus.OWNED));

        assertTrue(data.overlapsAny(new BlockPos(10, 0, 10), new BlockPos(20, 255, 20), 0));
    }

    @Test
    void noOverlapForDisjointBox() {
        SumPlotsWorldSavedData data = new SumPlotsWorldSavedData();
        data.addPlot(plot(new BlockPos(0, 0, 0), new BlockPos(10, 255, 10), 0, PlotStatus.OWNED));

        assertFalse(data.overlapsAny(new BlockPos(20, 0, 20), new BlockPos(30, 255, 30), 0));
    }

    @Test
    void overlapIsPerDimension() {
        SumPlotsWorldSavedData data = new SumPlotsWorldSavedData();
        data.addPlot(plot(new BlockPos(0, 0, 0), new BlockPos(10, 255, 10), 0, PlotStatus.OWNED));

        // Same coords, different dimension → not an overlap.
        assertFalse(data.overlapsAny(new BlockPos(0, 0, 0), new BlockPos(10, 255, 10), -1));
    }

    @Test
    void overlapHandlesReversedCandidateCorners() {
        SumPlotsWorldSavedData data = new SumPlotsWorldSavedData();
        data.addPlot(plot(new BlockPos(0, 0, 0), new BlockPos(10, 255, 10), 0, PlotStatus.OWNED));

        // Candidate corners passed max-first; overlapsAny normalizes internally.
        assertTrue(data.overlapsAny(new BlockPos(15, 255, 15), new BlockPos(5, 0, 5), 0));
    }

    @Test
    void addAndLookupById() {
        SumPlotsWorldSavedData data = new SumPlotsWorldSavedData();
        SumPlot p = plot(new BlockPos(0, 0, 0), new BlockPos(4, 4, 4), 0, PlotStatus.OWNED);
        data.addPlot(p);

        assertSame(p, data.getById(p.getPlotId()));
        assertEquals(1, data.getAll().size());
    }

    @Test
    void removePlotById() {
        SumPlotsWorldSavedData data = new SumPlotsWorldSavedData();
        SumPlot p = plot(new BlockPos(0, 0, 0), new BlockPos(4, 4, 4), 0, PlotStatus.OWNED);
        data.addPlot(p);

        assertTrue(data.removePlot(p.getPlotId()));
        assertFalse(data.removePlot(p.getPlotId()), "second removal is a no-op");
        assertTrue(data.getAll().isEmpty());
    }

    @Test
    void getPlotsForSaleFiltersByStatus() {
        SumPlotsWorldSavedData data = new SumPlotsWorldSavedData();
        SumPlot forSale = plot(new BlockPos(0, 0, 0), new BlockPos(4, 4, 4), 0, PlotStatus.FOR_SALE);
        data.addPlot(forSale);
        data.addPlot(plot(new BlockPos(20, 0, 20), new BlockPos(24, 4, 24), 0, PlotStatus.OWNED));
        data.addPlot(plot(new BlockPos(40, 0, 40), new BlockPos(44, 4, 44), 0, PlotStatus.RESERVED));

        List<SumPlot> sale = data.getPlotsForSale();
        assertEquals(1, sale.size());
        assertSame(forSale, sale.get(0));
    }

    @Test
    void getPlotsOwnedByMatchesOwnerUuid() {
        SumPlotsWorldSavedData data = new SumPlotsWorldSavedData();
        UUID owner = UUID.randomUUID();
        SumPlot mine = plot(new BlockPos(0, 0, 0), new BlockPos(4, 4, 4), 0, PlotStatus.OWNED);
        mine.setOwner(owner, "Me");
        SumPlot theirs = plot(new BlockPos(20, 0, 20), new BlockPos(24, 4, 24), 0, PlotStatus.OWNED);
        theirs.setOwner(UUID.randomUUID(), "Them");
        data.addPlot(mine);
        data.addPlot(theirs);

        List<SumPlot> owned = data.getPlotsOwnedBy(owner);
        assertEquals(1, owned.size());
        assertSame(mine, owned.get(0));
    }

    @Test
    void getPlotsContainingUsesChunkIndex() {
        SumPlotsWorldSavedData data = new SumPlotsWorldSavedData();
        SumPlot p = plot(new BlockPos(0, 0, 0), new BlockPos(15, 255, 15), 0, PlotStatus.OWNED);
        data.addPlot(p);

        assertEquals(1, data.getPlotsContaining(new BlockPos(8, 64, 8)).size());
        assertTrue(data.getPlotsContaining(new BlockPos(100, 64, 100)).isEmpty());
    }

    @Test
    void getPlotsNearUsesDistanceToBoundingBox() {
        SumPlotsWorldSavedData data = new SumPlotsWorldSavedData();
        SumPlot near = plot(new BlockPos(0, 0, 0), new BlockPos(10, 255, 10), 0, PlotStatus.OWNED);
        SumPlot far = plot(new BlockPos(500, 0, 500), new BlockPos(510, 255, 510), 0, PlotStatus.OWNED);
        data.addPlot(near);
        data.addPlot(far);

        // Center is 5 blocks outside the near plot's east edge, far from the other.
        List<SumPlot> hits = data.getPlotsNear(new BlockPos(15, 64, 5), 8);
        assertEquals(1, hits.size());
        assertSame(near, hits.get(0));
    }

    @Test
    void getPlotsNearIsInclusiveAtRadius() {
        SumPlotsWorldSavedData data = new SumPlotsWorldSavedData();
        SumPlot p = plot(new BlockPos(0, 0, 0), new BlockPos(10, 255, 10), 0, PlotStatus.OWNED);
        data.addPlot(p);

        // Manhattan distance from (20,10) to the bbox edge (10,10) is exactly 10.
        assertEquals(1, data.getPlotsNear(new BlockPos(20, 64, 10), 10).size());
        assertTrue(data.getPlotsNear(new BlockPos(20, 64, 10), 9).isEmpty());
    }
}
