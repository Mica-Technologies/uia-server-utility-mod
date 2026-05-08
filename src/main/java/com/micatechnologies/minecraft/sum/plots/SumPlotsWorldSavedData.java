package com.micatechnologies.minecraft.sum.plots;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

/**
 * Per-dimension persistent storage for {@link SumPlot}s, plus an in-memory chunk index for
 * fast contains() lookups during protection events. The chunk index is rebuilt from the
 * plot list on load (deserialized state) and incrementally maintained on add/remove.
 *
 * <p>One instance per dimension via {@link MapStorage}. The protection handler reaches in via
 * {@link #get(World)} from event listeners that already know which world the event came from.
 */
public class SumPlotsWorldSavedData extends WorldSavedData {

    private static final String NAME = "sum_plots";

    private final Map<UUID, SumPlot> plotsById = new LinkedHashMap<>();
    private final SumPlotsChunkIndex chunkIndex = new SumPlotsChunkIndex();

    public SumPlotsWorldSavedData() { super(NAME); }
    public SumPlotsWorldSavedData(String name) { super(name); }

    public static SumPlotsWorldSavedData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        if (storage == null) return new SumPlotsWorldSavedData();
        SumPlotsWorldSavedData data =
            (SumPlotsWorldSavedData) storage.getOrLoadData(SumPlotsWorldSavedData.class, NAME);
        if (data == null) {
            data = new SumPlotsWorldSavedData();
            storage.setData(NAME, data);
        }
        return data;
    }

    // --- Mutators ---

    public void addPlot(SumPlot plot) {
        plotsById.put(plot.getPlotId(), plot);
        chunkIndex.addPlot(plot);
        markDirty();
    }

    public boolean removePlot(UUID id) {
        SumPlot removed = plotsById.remove(id);
        if (removed != null) {
            chunkIndex.removePlot(removed);
            markDirty();
            return true;
        }
        return false;
    }

    public void touch() { markDirty(); }

    // --- Queries ---

    @Nullable
    public SumPlot getById(UUID id) { return plotsById.get(id); }

    public Collection<SumPlot> getAll() { return plotsById.values(); }

    /** All plots whose 3D bbox contains the given block position. May be empty. Uses the
     *  chunk index to skip plots that don't overlap the position's chunk. */
    public List<SumPlot> getPlotsContaining(BlockPos pos) {
        List<UUID> candidates = chunkIndex.plotIdsAt(pos);
        if (candidates.isEmpty()) return java.util.Collections.emptyList();
        List<SumPlot> hits = new ArrayList<>(candidates.size());
        for (UUID id : candidates) {
            SumPlot p = plotsById.get(id);
            if (p != null && p.contains(pos)) hits.add(p);
        }
        return hits;
    }

    /** Plots within a horizontal Manhattan radius of {@code center}. Used by
     *  {@code /sum plots list near}. */
    public List<SumPlot> getPlotsNear(BlockPos center, int radius) {
        List<SumPlot> hits = new ArrayList<>();
        long r = radius;
        for (SumPlot p : plotsById.values()) {
            BlockPos a = p.getCornerA();
            BlockPos b = p.getCornerB();
            // Closest point to center (per-axis clamp) — distance from center to bbox.
            int cx = clamp(center.getX(), a.getX(), b.getX());
            int cz = clamp(center.getZ(), a.getZ(), b.getZ());
            long dx = Math.abs(center.getX() - cx);
            long dz = Math.abs(center.getZ() - cz);
            if (dx + dz <= r) hits.add(p);
        }
        return hits;
    }

    public List<SumPlot> getPlotsForSale() {
        List<SumPlot> hits = new ArrayList<>();
        for (SumPlot p : plotsById.values()) {
            if (p.getStatus() == PlotStatus.FOR_SALE) hits.add(p);
        }
        return hits;
    }

    public List<SumPlot> getPlotsOwnedBy(UUID owner) {
        List<SumPlot> hits = new ArrayList<>();
        for (SumPlot p : plotsById.values()) {
            if (owner.equals(p.getOwnerUuid())) hits.add(p);
        }
        return hits;
    }

    /** True if any existing plot's bbox overlaps {@code candidate}. Used by /sum plots create
     *  to refuse overlapping creation (admins can override by deleting the conflicting plot
     *  first). */
    public boolean overlapsAny(BlockPos cornerA, BlockPos cornerB, int dimensionId) {
        int minX = Math.min(cornerA.getX(), cornerB.getX());
        int maxX = Math.max(cornerA.getX(), cornerB.getX());
        int minY = Math.min(cornerA.getY(), cornerB.getY());
        int maxY = Math.max(cornerA.getY(), cornerB.getY());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());
        for (SumPlot p : plotsById.values()) {
            if (p.getDimensionId() != dimensionId) continue;
            BlockPos a = p.getCornerA();
            BlockPos b = p.getCornerB();
            boolean overlap = !(maxX < a.getX() || minX > b.getX()
                || maxY < a.getY() || minY > b.getY()
                || maxZ < a.getZ() || minZ > b.getZ());
            if (overlap) return true;
        }
        return false;
    }

    public SumPlotsChunkIndex getChunkIndex() { return chunkIndex; }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // --- NBT ---

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        plotsById.clear();
        chunkIndex.clear();
        if (!nbt.hasKey("Plots")) return;
        NBTTagList list = nbt.getTagList("Plots", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            SumPlot p = SumPlot.readFromNBT(list.getCompoundTagAt(i));
            if (p != null) plotsById.put(p.getPlotId(), p);
        }
        chunkIndex.rebuild(plotsById.values());
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (SumPlot p : plotsById.values()) list.appendTag(p.writeToNBT());
        nbt.setTag("Plots", list);
        return nbt;
    }
}
