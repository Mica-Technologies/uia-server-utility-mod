package com.micatechnologies.minecraft.sum.plots;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.util.math.BlockPos;

/**
 * Eagerly-maintained chunk → plot-id index for O(1) protection-event lookups. Without this,
 * every BlockBreak/Place/Interact would scan every plot in the dimension to find a hit; with
 * it the protection handler does one map lookup per event.
 *
 * <p>Keys are {@code (chunkX, chunkZ)} packed into a long so the underlying {@link HashMap}
 * stays primitive-cheap. Values are lists because plots can overlap (admins can stack a
 * "rental zone" on top of an "owned plot" if they like — protection sees both and applies the
 * stricter rule).
 */
public class SumPlotsChunkIndex {

    private final Map<Long, List<UUID>> plotsByChunk = new HashMap<>();

    public void clear() {
        plotsByChunk.clear();
    }

    public void rebuild(Iterable<SumPlot> plots) {
        plotsByChunk.clear();
        for (SumPlot p : plots) addPlot(p);
    }

    public void addPlot(SumPlot plot) {
        for (long key : chunkKeysOf(plot)) {
            plotsByChunk.computeIfAbsent(key, k -> new ArrayList<>(2)).add(plot.getPlotId());
        }
    }

    public void removePlot(SumPlot plot) {
        for (long key : chunkKeysOf(plot)) {
            List<UUID> list = plotsByChunk.get(key);
            if (list != null) {
                list.remove(plot.getPlotId());
                if (list.isEmpty()) plotsByChunk.remove(key);
            }
        }
    }

    /** Plot ids registered for the chunk containing {@code pos}. May be empty. */
    public List<UUID> plotIdsAt(BlockPos pos) {
        List<UUID> list = plotsByChunk.get(packChunk(pos.getX() >> 4, pos.getZ() >> 4));
        return list == null ? java.util.Collections.emptyList() : list;
    }

    @Nullable
    public List<UUID> plotIdsAtChunk(int chunkX, int chunkZ) {
        return plotsByChunk.get(packChunk(chunkX, chunkZ));
    }

    private static long packChunk(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static long[] chunkKeysOf(SumPlot plot) {
        int dx = plot.getMaxChunkX() - plot.getMinChunkX() + 1;
        int dz = plot.getMaxChunkZ() - plot.getMinChunkZ() + 1;
        long[] keys = new long[dx * dz];
        int i = 0;
        for (int cx = plot.getMinChunkX(); cx <= plot.getMaxChunkX(); cx++) {
            for (int cz = plot.getMinChunkZ(); cz <= plot.getMaxChunkZ(); cz++) {
                keys[i++] = packChunk(cx, cz);
            }
        }
        return keys;
    }
}
