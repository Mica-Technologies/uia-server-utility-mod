package com.micatechnologies.minecraft.sum.jail;

import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/**
 * One jail cell: a named 3D box an inmate must stay inside, and the point they are put at.
 *
 * <p>The box is inclusive on every face, like a plot. The spawn point is where {@code /setjail}
 * was run from and must lie inside the box, so putting an inmate back always lands them in their
 * cell. Spawn yaw is kept so they face the way the operator did, usually away from the wall.
 */
public class JailCell {

    private final String name;
    private final int dimensionId;
    private final BlockPos min;
    private final BlockPos max;
    private final double spawnX;
    private final double spawnY;
    private final double spawnZ;
    private final float spawnYaw;

    public JailCell(String name, int dimensionId, BlockPos cornerA, BlockPos cornerB,
                    double spawnX, double spawnY, double spawnZ, float spawnYaw) {
        this.name = name;
        this.dimensionId = dimensionId;
        this.min = new BlockPos(Math.min(cornerA.getX(), cornerB.getX()),
            Math.min(cornerA.getY(), cornerB.getY()), Math.min(cornerA.getZ(), cornerB.getZ()));
        this.max = new BlockPos(Math.max(cornerA.getX(), cornerB.getX()),
            Math.max(cornerA.getY(), cornerB.getY()), Math.max(cornerA.getZ(), cornerB.getZ()));
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.spawnYaw = spawnYaw;
    }

    public String getName() { return name; }
    public int getDimensionId() { return dimensionId; }
    public BlockPos getMin() { return min; }
    public BlockPos getMax() { return max; }
    public double getSpawnX() { return spawnX; }
    public double getSpawnY() { return spawnY; }
    public double getSpawnZ() { return spawnZ; }
    public float getSpawnYaw() { return spawnYaw; }

    /** Map key for a cell name. Cell names are matched case-insensitively. */
    public static String key(String name) {
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    public boolean contains(int dimension, BlockPos pos) {
        return dimension == dimensionId
            && pos.getX() >= min.getX() && pos.getX() <= max.getX()
            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /**
     * Whether a player at these coordinates is inside the cell, judged by the block their feet
     * are in. That block is one above the floor, which is why {@code /setjail} refuses a spawn
     * point outside the box: a box drawn on the floor blocks alone would otherwise never contain
     * the inmate standing on them.
     */
    public boolean containsPlayerAt(int dimension, double x, double y, double z) {
        return contains(dimension, new BlockPos(x, y, z));
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Name", name);
        tag.setInteger("Dim", dimensionId);
        tag.setLong("Min", min.toLong());
        tag.setLong("Max", max.toLong());
        tag.setDouble("SpawnX", spawnX);
        tag.setDouble("SpawnY", spawnY);
        tag.setDouble("SpawnZ", spawnZ);
        tag.setFloat("SpawnYaw", spawnYaw);
        return tag;
    }

    @Nullable
    public static JailCell readFromNBT(NBTTagCompound tag) {
        if (!tag.hasKey("Name") || !tag.hasKey("Min") || !tag.hasKey("Max")) {
            return null;
        }
        return new JailCell(tag.getString("Name"), tag.getInteger("Dim"),
            BlockPos.fromLong(tag.getLong("Min")), BlockPos.fromLong(tag.getLong("Max")),
            tag.getDouble("SpawnX"), tag.getDouble("SpawnY"), tag.getDouble("SpawnZ"),
            tag.getFloat("SpawnYaw"));
    }
}
