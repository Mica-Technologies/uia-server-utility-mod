package com.micatechnologies.minecraft.sum.jobs;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;

/**
 * A single job-board entry. Created by a player via {@code /sum job post}, stored in
 * {@link JobBoardSavedData}, and rendered in {@link GuiJobBoard}.
 *
 * <p>The {@link #reward} is escrowed: the poster is charged when the listing is created, the
 * money is held by the listing, and it is paid out to the worker on approval or refunded to the
 * poster on cancel. The {@link #status} and worker fields are mutable; everything else is fixed
 * at post time.
 */
public class JobListing {

    public final UUID id;
    public final UUID posterUuid;
    public final String posterName;
    public final String description;
    public final double reward;
    public final long postedAt;        // epoch millis
    public final long expiresAt;       // epoch millis

    /** Mutable lifecycle state — see {@link JobStatus}. */
    public JobStatus status = JobStatus.OPEN;
    /** The worker who claimed it, or null while OPEN. */
    @Nullable public UUID workerUuid;
    public String workerName = "";

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

    /**
     * Formats a remaining-time span (millis until expiry) as a short "N{m,h,d} left" label,
     * or "expired" once it runs out. Pure so {@link GuiJobBoard} can render it and tests can
     * pin the bucket boundaries without a clock.
     */
    public static String formatRemaining(long remainingMs) {
        if (remainingMs <= 0) return "expired";
        if (remainingMs < 60_000L) return "<1m left";
        if (remainingMs < 3_600_000L) return (remainingMs / 60_000L) + "m left";
        if (remainingMs < 86_400_000L) return (remainingMs / 3_600_000L) + "h left";
        return (remainingMs / 86_400_000L) + "d left";
    }

    public boolean isPoster(UUID uuid) {
        return posterUuid.equals(uuid);
    }

    public boolean isWorker(UUID uuid) {
        return workerUuid != null && workerUuid.equals(uuid);
    }

    /** Claims the listing for a worker. */
    public void claim(UUID worker, String name) {
        this.workerUuid = worker;
        this.workerName = name == null ? "" : name;
        this.status = JobStatus.CLAIMED;
    }

    /** Releases any claim and returns the listing to the open pool. */
    public void reopen() {
        this.workerUuid = null;
        this.workerName = "";
        this.status = JobStatus.OPEN;
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
        nbt.setByte("Status", (byte) status.ordinal());
        if (workerUuid != null) {
            nbt.setUniqueId("Worker", workerUuid);
            nbt.setString("WorkerName", workerName);
        }
        return nbt;
    }

    @Nullable
    public static JobListing readFromNBT(NBTTagCompound nbt) {
        if (!nbt.hasUniqueId("Id") || !nbt.hasUniqueId("Poster")) return null;
        JobListing l = new JobListing(
            nbt.getUniqueId("Id"),
            nbt.getUniqueId("Poster"),
            nbt.getString("PosterName"),
            nbt.getString("Description"),
            nbt.getDouble("Reward"),
            nbt.getLong("PostedAt"),
            nbt.getLong("ExpiresAt"));
        l.status = JobStatus.fromOrdinal(nbt.getByte("Status"));
        if (nbt.hasUniqueId("Worker")) {
            l.workerUuid = nbt.getUniqueId("Worker");
            l.workerName = nbt.getString("WorkerName");
        }
        // A worker field with an OPEN status (or vice-versa) shouldn't happen, but normalize.
        if (l.workerUuid == null && l.status != JobStatus.OPEN) {
            l.status = JobStatus.OPEN;
        }
        return l;
    }

    /** Serializes the full listing for the client snapshot ({@link PacketOpenJobBoard}). */
    public void writeTo(ByteBuf buf) {
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
        buf.writeLong(posterUuid.getMostSignificantBits());
        buf.writeLong(posterUuid.getLeastSignificantBits());
        ByteBufUtils.writeUTF8String(buf, posterName);
        ByteBufUtils.writeUTF8String(buf, description);
        buf.writeDouble(reward);
        buf.writeLong(postedAt);
        buf.writeLong(expiresAt);
        buf.writeByte(status.ordinal());
        boolean hasWorker = workerUuid != null;
        buf.writeBoolean(hasWorker);
        if (hasWorker) {
            buf.writeLong(workerUuid.getMostSignificantBits());
            buf.writeLong(workerUuid.getLeastSignificantBits());
            ByteBufUtils.writeUTF8String(buf, workerName);
        }
    }

    public static JobListing readFrom(ByteBuf buf) {
        UUID id = new UUID(buf.readLong(), buf.readLong());
        UUID poster = new UUID(buf.readLong(), buf.readLong());
        String posterName = ByteBufUtils.readUTF8String(buf);
        String description = ByteBufUtils.readUTF8String(buf);
        double reward = buf.readDouble();
        long postedAt = buf.readLong();
        long expiresAt = buf.readLong();
        JobListing l = new JobListing(id, poster, posterName, description, reward, postedAt, expiresAt);
        l.status = JobStatus.fromOrdinal(buf.readByte());
        if (buf.readBoolean()) {
            l.workerUuid = new UUID(buf.readLong(), buf.readLong());
            l.workerName = ByteBufUtils.readUTF8String(buf);
        }
        return l;
    }
}
