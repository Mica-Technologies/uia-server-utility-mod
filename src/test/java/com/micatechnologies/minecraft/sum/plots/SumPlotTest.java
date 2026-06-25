package com.micatechnologies.minecraft.sum.plots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for {@link SumPlot}: corner normalization, bbox containment, volume, and
 * NBT round-trip. Covers TESTING_PLAN.md §4.4 D2 (overlap/volume reporting) at the unit level,
 * and guards the persistence path that bit the safe-deposit box ({@code 7c74d4e}).
 */
class SumPlotTest {

    private static SumPlot plot(BlockPos a, BlockPos b) {
        return new SumPlot(UUID.randomUUID(), "test", a, b, 0, 100.0, PlotStatus.FOR_SALE, 0L);
    }

    @Test
    void normalizesCornersRegardlessOfClickOrder() {
        // Corner A is "max" in the input, corner B is "min" — constructor must sort them.
        SumPlot p = plot(new BlockPos(10, 70, 10), new BlockPos(0, 60, -5));
        assertEquals(new BlockPos(0, 60, -5), p.getCornerA(), "cornerA should be the per-axis min");
        assertEquals(new BlockPos(10, 70, 10), p.getCornerB(), "cornerB should be the per-axis max");
    }

    @Test
    void normalizesMixedAxisCorners() {
        // Each axis picks its own min/max independently.
        SumPlot p = plot(new BlockPos(10, 0, -5), new BlockPos(-3, 255, 8));
        assertEquals(new BlockPos(-3, 0, -5), p.getCornerA());
        assertEquals(new BlockPos(10, 255, 8), p.getCornerB());
    }

    @Test
    void containsIsInclusiveOnBoundaries() {
        SumPlot p = plot(new BlockPos(0, 60, 0), new BlockPos(10, 70, 10));
        assertTrue(p.contains(new BlockPos(5, 65, 5)), "interior point");
        assertTrue(p.contains(new BlockPos(0, 60, 0)), "min corner is inside");
        assertTrue(p.contains(new BlockPos(10, 70, 10)), "max corner is inside");
        assertTrue(p.contains(new BlockPos(0, 70, 10)), "edge point");
    }

    @Test
    void containsRejectsOutsidePoints() {
        SumPlot p = plot(new BlockPos(0, 60, 0), new BlockPos(10, 70, 10));
        assertFalse(p.contains(new BlockPos(-1, 65, 5)), "x below");
        assertFalse(p.contains(new BlockPos(11, 65, 5)), "x above");
        assertFalse(p.contains(new BlockPos(5, 59, 5)), "y below");
        assertFalse(p.contains(new BlockPos(5, 71, 5)), "y above");
        assertFalse(p.contains(new BlockPos(5, 65, -1)), "z below");
        assertFalse(p.contains(new BlockPos(5, 65, 11)), "z above");
    }

    @Test
    void volumeOfSingleBlockIsOne() {
        SumPlot p = plot(new BlockPos(7, 64, -3), new BlockPos(7, 64, -3));
        assertEquals(1L, p.volume());
    }

    @Test
    void volumeIsInclusiveOnAllAxes() {
        // 2x2x2 cube of blocks.
        SumPlot p = plot(new BlockPos(0, 0, 0), new BlockPos(1, 1, 1));
        assertEquals(8L, p.volume());
    }

    @Test
    void volumeOfFullColumnChunkFootprintMatchesBrowserLabel() {
        // 16x16 footprint, full y column 0..255 → the "vol 65k" the plot browser shows.
        SumPlot p = plot(new BlockPos(0, 0, 0), new BlockPos(15, 255, 15));
        assertEquals(65536L, p.volume());
    }

    @Test
    void volumeDoesNotOverflowOnLargeClaims() {
        // A claim large enough to overflow a 32-bit int product; volume() uses longs.
        SumPlot p = plot(new BlockPos(0, 0, 0), new BlockPos(3000, 255, 3000));
        long expected = 3001L * 256L * 3001L;
        assertEquals(expected, p.volume());
        assertTrue(p.volume() > Integer.MAX_VALUE, "must not have wrapped negative/int");
    }

    @Test
    void unownedPlotIsBuildableByAnyone() {
        // canBuild short-circuits on a null owner before ever touching the player, so we can
        // pass null here safely — this is the "unowned plot = open build" rule.
        SumPlot p = plot(new BlockPos(0, 0, 0), new BlockPos(4, 4, 4));
        assertTrue(p.canBuild(null));
    }

    @Test
    void chunkBoundsDeriveFromCorners() {
        SumPlot p = plot(new BlockPos(0, 0, 0), new BlockPos(31, 255, 47));
        assertEquals(0, p.getMinChunkX());
        assertEquals(1, p.getMaxChunkX());
        assertEquals(0, p.getMinChunkZ());
        assertEquals(2, p.getMaxChunkZ());
    }

    @Test
    void nbtRoundTripPreservesAllFields() {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID trusted = UUID.randomUUID();
        SumPlot original = new SumPlot(id, "Downtown Lot", new BlockPos(-10, 0, -10),
            new BlockPos(20, 200, 25), -1, 250.0, PlotStatus.OWNED, 1234L);
        original.setOwner(owner, "Alice");
        original.addTrustedBuilder(trusted);

        SumPlot restored = SumPlot.readFromNBT(original.writeToNBT());

        assertEquals(id, restored.getPlotId());
        assertEquals("Downtown Lot", restored.getDisplayName());
        assertEquals(owner, restored.getOwnerUuid());
        assertEquals("Alice", restored.getOwnerName());
        assertEquals(new BlockPos(-10, 0, -10), restored.getCornerA());
        assertEquals(new BlockPos(20, 200, 25), restored.getCornerB());
        assertEquals(-1, restored.getDimensionId());
        assertEquals(250.0, restored.getPrice());
        assertEquals(PlotStatus.OWNED, restored.getStatus());
        assertEquals(1234L, restored.getCreatedAt());
        assertTrue(restored.getTrustedBuilders().contains(trusted));
    }

    @Test
    void nbtRoundTripKeepsUnownedPlotOwnerless() {
        SumPlot original = plot(new BlockPos(0, 0, 0), new BlockPos(5, 5, 5));
        SumPlot restored = SumPlot.readFromNBT(original.writeToNBT());
        // No owner was set; round-trip must not invent one.
        assertEquals(null, restored.getOwnerUuid());
        assertEquals("", restored.getOwnerName());
        assertTrue(restored.getTrustedBuilders().isEmpty());
    }

    @Test
    void readFromNbtReturnsNullWithoutId() {
        // A blank/foreign compound is not a plot.
        assertEquals(null, SumPlot.readFromNBT(new NBTTagCompound()));
    }
}
