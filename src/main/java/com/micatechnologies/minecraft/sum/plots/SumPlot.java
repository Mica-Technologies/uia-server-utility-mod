package com.micatechnologies.minecraft.sum.plots;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

/**
 * One claimed land plot. Created by admins via {@code /sum plots create}, bought by players
 * via {@code /sum plots buy}, protected by {@link PlotsProtectionHandler}.
 *
 * <p>The bounding box is a 3D AABB ({@link #cornerA}..{@link #cornerB}); the {@code y} bounds
 * default to full-column when an admin creates with the wand (corner A.y = 0, corner B.y =
 * 255), but custom 3D claims are supported by setting different y values explicitly.
 *
 * <p>Trusted builders bypass protection without owning the plot. An admin
 * (op-level) bypasses protection regardless.
 */
public class SumPlot {

    private final UUID plotId;
    private String displayName;
    @Nullable private UUID ownerUuid;
    private String ownerName = "";
    private final BlockPos cornerA;
    private final BlockPos cornerB;
    private final int dimensionId;
    private double price;
    private PlotStatus status;
    private final Set<UUID> trustedBuilders = new LinkedHashSet<>();
    private final long createdAt;
    private long lastActivity;

    public SumPlot(UUID plotId, String displayName, BlockPos cornerA, BlockPos cornerB,
                   int dimensionId, double price, PlotStatus status, long createdAt) {
        this.plotId = plotId;
        this.displayName = displayName;
        this.cornerA = normalize(cornerA, cornerB, true);
        this.cornerB = normalize(cornerA, cornerB, false);
        this.dimensionId = dimensionId;
        this.price = price;
        this.status = status;
        this.createdAt = createdAt;
        this.lastActivity = createdAt;
    }

    /** Pick the min-or-max corner so {@link #contains} works regardless of which corner the
     *  admin clicked first with the wand. */
    private static BlockPos normalize(BlockPos a, BlockPos b, boolean min) {
        return new BlockPos(
            min ? Math.min(a.getX(), b.getX()) : Math.max(a.getX(), b.getX()),
            min ? Math.min(a.getY(), b.getY()) : Math.max(a.getY(), b.getY()),
            min ? Math.min(a.getZ(), b.getZ()) : Math.max(a.getZ(), b.getZ()));
    }

    // --- Getters ---

    public UUID getPlotId() { return plotId; }
    public String getDisplayName() { return displayName; }
    @Nullable public UUID getOwnerUuid() { return ownerUuid; }
    public String getOwnerName() { return ownerName; }
    public BlockPos getCornerA() { return cornerA; }
    public BlockPos getCornerB() { return cornerB; }
    public int getDimensionId() { return dimensionId; }
    public double getPrice() { return price; }
    public PlotStatus getStatus() { return status; }
    public Set<UUID> getTrustedBuilders() { return trustedBuilders; }
    public long getCreatedAt() { return createdAt; }
    public long getLastActivity() { return lastActivity; }

    // --- Setters (the saved-data layer is responsible for markDirty) ---

    public void setDisplayName(String name) { this.displayName = name; }

    public void setOwner(@Nullable UUID uuid, String name) {
        this.ownerUuid = uuid;
        this.ownerName = name == null ? "" : name;
        touch();
    }

    public void setPrice(double price) { this.price = price; touch(); }

    public void setStatus(PlotStatus status) { this.status = status; touch(); }

    public void addTrustedBuilder(UUID uuid) { trustedBuilders.add(uuid); touch(); }

    public boolean removeTrustedBuilder(UUID uuid) {
        boolean removed = trustedBuilders.remove(uuid);
        if (removed) touch();
        return removed;
    }

    public void touch() { this.lastActivity = System.currentTimeMillis(); }

    // --- Helpers ---

    /** True if {@code pos} is inside the 3D bbox. */
    public boolean contains(BlockPos pos) {
        return pos.getX() >= cornerA.getX() && pos.getX() <= cornerB.getX()
            && pos.getY() >= cornerA.getY() && pos.getY() <= cornerB.getY()
            && pos.getZ() >= cornerA.getZ() && pos.getZ() <= cornerB.getZ();
    }

    /** True if the player can build/break inside this plot:
     *  owner, trusted builder, op (caller checks separately), or unowned plot. */
    public boolean canBuild(EntityPlayer player) {
        if (ownerUuid == null) return true;
        UUID id = player.getUniqueID();
        return ownerUuid.equals(id) || trustedBuilders.contains(id);
    }

    public long volume() {
        long dx = (long) (cornerB.getX() - cornerA.getX()) + 1;
        long dy = (long) (cornerB.getY() - cornerA.getY()) + 1;
        long dz = (long) (cornerB.getZ() - cornerA.getZ()) + 1;
        return dx * dy * dz;
    }

    /** Min chunk-X covered (for the chunk index). */
    public int getMinChunkX() { return cornerA.getX() >> 4; }
    public int getMaxChunkX() { return cornerB.getX() >> 4; }
    public int getMinChunkZ() { return cornerA.getZ() >> 4; }
    public int getMaxChunkZ() { return cornerB.getZ() >> 4; }

    // --- NBT ---

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setUniqueId("Id", plotId);
        nbt.setString("Name", displayName);
        if (ownerUuid != null) nbt.setUniqueId("Owner", ownerUuid);
        nbt.setString("OwnerName", ownerName);
        nbt.setLong("CornerA", cornerA.toLong());
        nbt.setLong("CornerB", cornerB.toLong());
        nbt.setInteger("Dim", dimensionId);
        nbt.setDouble("Price", price);
        nbt.setString("Status", status.name());
        nbt.setLong("CreatedAt", createdAt);
        nbt.setLong("LastActivity", lastActivity);
        NBTTagList trusted = new NBTTagList();
        for (UUID id : trustedBuilders) {
            NBTTagCompound t = new NBTTagCompound();
            t.setUniqueId("U", id);
            trusted.appendTag(t);
        }
        nbt.setTag("Trusted", trusted);
        return nbt;
    }

    @Nullable
    public static SumPlot readFromNBT(NBTTagCompound nbt) {
        if (!nbt.hasUniqueId("Id")) return null;
        UUID id = nbt.getUniqueId("Id");
        String name = nbt.getString("Name");
        BlockPos a = BlockPos.fromLong(nbt.getLong("CornerA"));
        BlockPos b = BlockPos.fromLong(nbt.getLong("CornerB"));
        int dim = nbt.getInteger("Dim");
        double price = nbt.getDouble("Price");
        PlotStatus status = PlotStatus.fromName(nbt.getString("Status"), PlotStatus.OWNED);
        long createdAt = nbt.getLong("CreatedAt");
        SumPlot plot = new SumPlot(id, name, a, b, dim, price, status, createdAt);
        if (nbt.hasUniqueId("Owner")) {
            plot.ownerUuid = nbt.getUniqueId("Owner");
        }
        plot.ownerName = nbt.getString("OwnerName");
        plot.lastActivity = nbt.getLong("LastActivity");
        if (nbt.hasKey("Trusted")) {
            NBTTagList list = nbt.getTagList("Trusted", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound t = list.getCompoundTagAt(i);
                if (t.hasUniqueId("U")) plot.trustedBuilders.add(t.getUniqueId("U"));
            }
        }
        return plot;
    }
}
