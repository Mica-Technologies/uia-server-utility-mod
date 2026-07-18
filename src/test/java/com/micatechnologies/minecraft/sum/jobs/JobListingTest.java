package com.micatechnologies.minecraft.sum.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@link JobListing} data model: expiry boundary, poster/worker identity, the
 * claim/reopen lifecycle, and both serialization round-trips (NBT for storage, ByteBuf for the
 * client snapshot) — including the "worker-less non-OPEN status normalizes to OPEN" repair.
 */
class JobListingTest {

    private static final double EPS = 1e-9;

    private static JobListing open(long postedAt, long expiresAt) {
        return new JobListing(UUID.randomUUID(), UUID.randomUUID(), "Poster", "chop wood",
            100.0, postedAt, expiresAt);
    }

    @Test
    void isExpiredIsInclusiveAtExpiry() {
        JobListing l = open(0, 1000);
        assertFalse(l.isExpired(999));
        assertTrue(l.isExpired(1000), "expiry instant counts as expired");
        assertTrue(l.isExpired(1001));
    }

    @Test
    void isPosterMatchesPosterOnly() {
        JobListing l = open(0, 1000);
        assertTrue(l.isPoster(l.posterUuid));
        assertFalse(l.isPoster(UUID.randomUUID()));
    }

    @Test
    void isWorkerFalseWhileOpen() {
        JobListing l = open(0, 1000);
        assertFalse(l.isWorker(l.posterUuid));
        assertFalse(l.isWorker(UUID.randomUUID()));
    }

    @Test
    void claimSetsWorkerAndStatus() {
        JobListing l = open(0, 1000);
        UUID worker = UUID.randomUUID();
        l.claim(worker, "Bob");
        assertSame(JobStatus.CLAIMED, l.status);
        assertEquals(worker, l.workerUuid);
        assertEquals("Bob", l.workerName);
        assertTrue(l.isWorker(worker));
        assertFalse(l.isWorker(l.posterUuid));
    }

    @Test
    void claimCoercesNullNameToEmpty() {
        JobListing l = open(0, 1000);
        l.claim(UUID.randomUUID(), null);
        assertEquals("", l.workerName);
    }

    @Test
    void reopenClearsClaim() {
        JobListing l = open(0, 1000);
        l.claim(UUID.randomUUID(), "Bob");
        l.reopen();
        assertSame(JobStatus.OPEN, l.status);
        assertNull(l.workerUuid);
        assertEquals("", l.workerName);
    }

    @Test
    void nbtRoundTripOpenListing() {
        JobListing l = open(111L, 222L);
        JobListing r = JobListing.readFromNBT(l.writeToNBT());
        assertEquals(l.id, r.id);
        assertEquals(l.posterUuid, r.posterUuid);
        assertEquals("Poster", r.posterName);
        assertEquals("chop wood", r.description);
        assertEquals(100.0, r.reward, EPS);
        assertEquals(111L, r.postedAt);
        assertEquals(222L, r.expiresAt);
        assertSame(JobStatus.OPEN, r.status);
        assertNull(r.workerUuid);
    }

    @Test
    void nbtRoundTripClaimedListing() {
        JobListing l = open(0, 1000);
        UUID worker = UUID.randomUUID();
        l.claim(worker, "Bob");
        JobListing r = JobListing.readFromNBT(l.writeToNBT());
        assertSame(JobStatus.CLAIMED, r.status);
        assertEquals(worker, r.workerUuid);
        assertEquals("Bob", r.workerName);
    }

    @Test
    void nbtWithoutIdOrPosterIsNull() {
        assertNull(JobListing.readFromNBT(new NBTTagCompound()));
        NBTTagCompound onlyId = new NBTTagCompound();
        onlyId.setUniqueId("Id", UUID.randomUUID());
        assertNull(JobListing.readFromNBT(onlyId), "missing Poster → null");
    }

    @Test
    void workerlessNonOpenStatusNormalizesToOpen() {
        // A SUBMITTED status with no worker field can't be valid; reading repairs it to OPEN.
        NBTTagCompound nbt = open(0, 1000).writeToNBT();
        nbt.setByte("Status", (byte) JobStatus.SUBMITTED.ordinal());
        JobListing r = JobListing.readFromNBT(nbt);
        assertSame(JobStatus.OPEN, r.status);
    }

    @Test
    void byteBufRoundTripOpenAndClaimed() {
        JobListing open = open(5L, 10L);
        JobListing openOut = byteBufRoundTrip(open);
        assertEquals(open.id, openOut.id);
        assertEquals("chop wood", openOut.description);
        assertSame(JobStatus.OPEN, openOut.status);
        assertNull(openOut.workerUuid);

        JobListing claimed = open(5L, 10L);
        UUID worker = UUID.randomUUID();
        claimed.claim(worker, "Bob");
        JobListing claimedOut = byteBufRoundTrip(claimed);
        assertSame(JobStatus.CLAIMED, claimedOut.status);
        assertEquals(worker, claimedOut.workerUuid);
        assertEquals("Bob", claimedOut.workerName);
    }

    @Test
    void formatRemainingBucketsByMagnitude() {
        assertEquals("expired", JobListing.formatRemaining(0));
        assertEquals("expired", JobListing.formatRemaining(-5));
        assertEquals("<1m left", JobListing.formatRemaining(59_999L));
        assertEquals("1m left", JobListing.formatRemaining(60_000L));
        assertEquals("59m left", JobListing.formatRemaining(3_599_999L));
        assertEquals("1h left", JobListing.formatRemaining(3_600_000L));
        assertEquals("23h left", JobListing.formatRemaining(86_399_999L));
        assertEquals("1d left", JobListing.formatRemaining(86_400_000L));
        assertEquals("2d left", JobListing.formatRemaining(2 * 86_400_000L));
    }

    private static JobListing byteBufRoundTrip(JobListing l) {
        ByteBuf buf = Unpooled.buffer();
        l.writeTo(buf);
        return JobListing.readFrom(buf);
    }
}
