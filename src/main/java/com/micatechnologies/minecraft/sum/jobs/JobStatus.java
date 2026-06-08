package com.micatechnologies.minecraft.sum.jobs;

/**
 * Lifecycle state of a {@link JobListing}. A completed (approved-and-paid) listing is removed
 * outright rather than kept in a terminal state.
 *
 * <ul>
 *   <li>{@link #OPEN} — posted, escrow held, awaiting a worker.</li>
 *   <li>{@link #CLAIMED} — a worker has accepted and is doing the job.</li>
 *   <li>{@link #SUBMITTED} — the worker marked it done; awaiting poster approval.</li>
 * </ul>
 */
public enum JobStatus {
    OPEN,
    CLAIMED,
    SUBMITTED;

    public static JobStatus fromOrdinal(int ordinal) {
        JobStatus[] values = values();
        return (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : OPEN;
    }
}
