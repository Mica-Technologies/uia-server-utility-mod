package com.micatechnologies.minecraft.sum.jobs;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;

/**
 * A single job-board entry. Created by a player via the {@code /sum job post} command,
 * stored in {@link JobBoardSavedData}, and rendered in {@link GuiJobBoard}.
 */
public class JobListing {

    public final UUID id;
    public final UUID posterUuid;
    public final String posterName;
    public final String description;
    public final double reward;
    public final long postedAt;        // epoch millis
    public final long expiresAt;       // epoch millis

    public JobListing(UUID id, UUID posterUuid, String posterName, String description,
                      double reward, long postedAt, long expiresAt) {
        this.id = id;
        this.posterUuid = posterUuid;
        this.posterName = posterName;
        this.description = description;
        this.reward = reward;
        this.postedAt = postedAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(long nowMillis) {
        return nowMillis >= expiresAt;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setUniqueId("Id", id);
        nbt.setUniqueId("Poster", posterUuid);
        nbt.setString("PosterName", posterName);
        nbt.setString("Description", description);
        nbt.setDouble("Reward", reward);
        nbt.setLong("PostedAt", postedAt);
        nbt.setLong("ExpiresAt", expiresAt);
        return nbt;
    }

    @Nullable
    public static JobListing readFromNBT(NBTTagCompound nbt) {
        if (!nbt.hasUniqueId("Id") || !nbt.hasUniqueId("Poster")) return null;
        return new JobListing(
            nbt.getUniqueId("Id"),
            nbt.getUniqueId("Poster"),
            nbt.getString("PosterName"),
            nbt.getString("Description"),
            nbt.getDouble("Reward"),
            nbt.getLong("PostedAt"),
            nbt.getLong("ExpiresAt"));
    }
}
