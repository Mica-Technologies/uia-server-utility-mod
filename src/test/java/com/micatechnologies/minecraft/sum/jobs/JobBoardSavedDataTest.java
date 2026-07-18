package com.micatechnologies.minecraft.sum.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

/**
 * Tests the pure list-management logic of {@link JobBoardSavedData}: active-filtering + newest-
 * first sort, open counting, poster-scoped takes/removes, expiry sweep, and the NBT round-trip.
 * {@code get(World)} is MC-coupled; everything here works on a bare no-arg instance (matching the
 * existing plots {@code WorldSavedData} tests), with {@code markDirty()} a harmless flag.
 */
class JobBoardSavedDataTest {

    private static final long NOW = 1000L;

    private static JobListing listing(UUID poster, long postedAt, long expiresAt, JobStatus status) {
        JobListing l = new JobListing(UUID.randomUUID(), poster, "P", "d", 50.0, postedAt, expiresAt);
        l.status = status;
        return l;
    }

    private static JobListing active(long postedAt) {
        return listing(UUID.randomUUID(), postedAt, NOW + 5000L, JobStatus.OPEN);
    }

    private static JobListing expired(UUID poster) {
        return listing(poster, 0L, NOW - 1L, JobStatus.OPEN);
    }

    @Test
    void getActiveExcludesExpiredAndSortsNewestFirst() {
        JobBoardSavedData d = new JobBoardSavedData();
        d.addListing(active(30L));
        d.addListing(active(10L));
        d.addListing(active(20L));
        d.addListing(expired(UUID.randomUUID()));

        List<JobListing> active = d.getActive(NOW);
        assertEquals(3, active.size(), "expired excluded");
        assertEquals(30L, active.get(0).postedAt);
        assertEquals(20L, active.get(1).postedAt);
        assertEquals(10L, active.get(2).postedAt);
    }

    @Test
    void getActiveEmptyWhenAllExpired() {
        JobBoardSavedData d = new JobBoardSavedData();
        d.addListing(expired(UUID.randomUUID()));
        d.addListing(expired(UUID.randomUUID()));
        assertTrue(d.getActive(NOW).isEmpty());
    }

    @Test
    void getOpenCountIgnoresExpiredAndNonOpen() {
        JobBoardSavedData d = new JobBoardSavedData();
        d.addListing(active(1L));                                              // OPEN, active
        d.addListing(listing(UUID.randomUUID(), 2L, NOW + 5000L, JobStatus.CLAIMED)); // not OPEN
        d.addListing(expired(UUID.randomUUID()));                             // OPEN but expired
        assertEquals(1, d.getOpenCount(NOW));
        assertEquals(0, new JobBoardSavedData().getOpenCount(NOW));
    }

    @Test
    void getByIdFindsRegardlessOfExpiry() {
        JobBoardSavedData d = new JobBoardSavedData();
        JobListing exp = expired(UUID.randomUUID());
        d.addListing(exp);
        assertNotNull(d.getById(exp.id), "expired listings are still resolvable by id");
        assertNull(d.getById(UUID.randomUUID()));
    }

    @Test
    void removeListingIsIdempotent() {
        JobBoardSavedData d = new JobBoardSavedData();
        JobListing l = active(1L);
        d.addListing(l);
        assertTrue(d.removeListing(l.id));
        assertFalse(d.removeListing(l.id), "second remove finds nothing");
    }

    @Test
    void takeExpiredForOnlyTakesThatPostersExpired() {
        UUID poster = UUID.randomUUID();
        JobBoardSavedData d = new JobBoardSavedData();
        JobListing mineExpired = expired(poster);
        JobListing mineActive = listing(poster, 5L, NOW + 5000L, JobStatus.OPEN);
        JobListing othersExpired = expired(UUID.randomUUID());
        d.addListing(mineExpired);
        d.addListing(mineActive);
        d.addListing(othersExpired);

        List<JobListing> taken = d.takeExpiredFor(poster, NOW);
        assertEquals(1, taken.size());
        assertEquals(mineExpired.id, taken.get(0).id);
        assertNotNull(d.getById(mineActive.id), "poster's active listing stays");
        assertNotNull(d.getById(othersExpired.id), "other poster's expired stays");
    }

    @Test
    void takeByPosterTakesAllRegardlessOfState() {
        UUID poster = UUID.randomUUID();
        JobBoardSavedData d = new JobBoardSavedData();
        d.addListing(listing(poster, 1L, NOW + 5000L, JobStatus.OPEN));
        d.addListing(listing(poster, 2L, NOW - 1L, JobStatus.CLAIMED));
        JobListing other = active(3L);
        d.addListing(other);

        List<JobListing> taken = d.takeByPoster(poster);
        assertEquals(2, taken.size());
        assertNotNull(d.getById(other.id), "another poster's listing is untouched");
    }

    @Test
    void removeByPosterReturnsCount() {
        UUID poster = UUID.randomUUID();
        JobBoardSavedData d = new JobBoardSavedData();
        d.addListing(listing(poster, 1L, NOW + 5000L, JobStatus.OPEN));
        d.addListing(listing(poster, 2L, NOW + 5000L, JobStatus.OPEN));
        d.addListing(active(3L));
        assertEquals(2, d.removeByPoster(poster));
        assertEquals(0, d.removeByPoster(poster), "nothing left to remove");
    }

    @Test
    void sweepExpiredDropsOnlyExpired() {
        JobBoardSavedData d = new JobBoardSavedData();
        d.addListing(active(1L));
        d.addListing(expired(UUID.randomUUID()));
        d.addListing(expired(UUID.randomUUID()));
        assertEquals(2, d.sweepExpired(NOW));
        assertEquals(1, d.getActive(NOW).size());
        assertEquals(0, d.sweepExpired(NOW), "second sweep finds nothing");
    }

    @Test
    void nbtRoundTripPreservesListings() {
        JobBoardSavedData d = new JobBoardSavedData();
        JobListing open = active(10L);
        JobListing claimed = active(20L);
        claimed.claim(UUID.randomUUID(), "Bob");
        d.addListing(open);
        d.addListing(claimed);

        NBTTagCompound nbt = d.writeToNBT(new NBTTagCompound());
        JobBoardSavedData restored = new JobBoardSavedData();
        restored.readFromNBT(nbt);

        assertNotNull(restored.getById(open.id));
        JobListing rc = restored.getById(claimed.id);
        assertNotNull(rc);
        assertEquals("Bob", rc.workerName);
        assertEquals(JobStatus.CLAIMED, rc.status);
    }

    @Test
    void readingMissingListingsKeyIsEmpty() {
        JobBoardSavedData d = new JobBoardSavedData();
        d.readFromNBT(new NBTTagCompound());
        assertTrue(d.getActive(NOW).isEmpty());
    }
}
