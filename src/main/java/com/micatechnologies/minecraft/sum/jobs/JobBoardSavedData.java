package com.micatechnologies.minecraft.sum.jobs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/**
 * Server-wide persistent storage for {@link JobListing}s. One instance per world (attached to
 * the overworld's MapStorage). Listings are visible from any job board on the server.
 *
 * <p>Lifecycle: callers call {@link #addListing}, {@link #removeListing}, or {@link #getActive}.
 * The latter filters out expired entries lazily (no background sweep — expired listings stick
 * around in the file but are never shown), which keeps the data layer simple.
 */
public class JobBoardSavedData extends WorldSavedData {

    private static final String NAME = "sum_jobs";

    private final List<JobListing> listings = new ArrayList<>();

    public JobBoardSavedData() {
        super(NAME);
    }

    public JobBoardSavedData(String name) {
        super(name);
    }

    public static JobBoardSavedData get(World world) {
        MapStorage storage = world.getMapStorage();
        if (storage == null) {
            // fall back to a transient instance if mapstorage isn't ready (shouldn't happen
            // in normal play, but defensive).
            return new JobBoardSavedData();
        }
        JobBoardSavedData data = (JobBoardSavedData) storage.getOrLoadData(JobBoardSavedData.class, NAME);
        if (data == null) {
            data = new JobBoardSavedData();
            storage.setData(NAME, data);
        }
        return data;
    }

    /** Inserts a listing and persists. */
    public void addListing(JobListing listing) {
        listings.add(listing);
        markDirty();
    }

    /** Removes a listing by id; returns true if found and removed. */
    public boolean removeListing(UUID id) {
        Iterator<JobListing> it = listings.iterator();
        while (it.hasNext()) {
            if (it.next().id.equals(id)) {
                it.remove();
                markDirty();
                return true;
            }
        }
        return false;
    }

    /** Returns active (non-expired) listings, newest-first. */
    public List<JobListing> getActive(long nowMillis) {
        List<JobListing> active = new ArrayList<>();
        for (JobListing l : listings) {
            if (!l.isExpired(nowMillis)) active.add(l);
        }
        active.sort(Comparator.comparingLong((JobListing l) -> l.postedAt).reversed());
        return active;
    }

    /** Removes every listing posted by the given player. Returns the number removed. */
    public int removeByPoster(UUID posterUuid) {
        int before = listings.size();
        listings.removeIf(l -> l.posterUuid.equals(posterUuid));
        int removed = before - listings.size();
        if (removed > 0) markDirty();
        return removed;
    }

    /** Drops every expired listing from the file. Optional cleanup; not auto-run. */
    public int sweepExpired(long nowMillis) {
        int before = listings.size();
        listings.removeIf(l -> l.isExpired(nowMillis));
        int removed = before - listings.size();
        if (removed > 0) markDirty();
        return removed;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        listings.clear();
        if (!nbt.hasKey("Listings")) return;
        NBTTagList list = nbt.getTagList("Listings", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            JobListing l = JobListing.readFromNBT(list.getCompoundTagAt(i));
            if (l != null) listings.add(l);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (JobListing l : listings) {
            list.appendTag(l.writeToNBT());
        }
        nbt.setTag("Listings", list);
        return nbt;
    }
}
