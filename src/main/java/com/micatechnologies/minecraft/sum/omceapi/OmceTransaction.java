package com.micatechnologies.minecraft.sum.omceapi;

import javax.annotation.Nullable;

/**
 * A transaction as reported by the service. Distinct from {@link OmceTransactionRequest}, which is
 * what SUM sends: this is what came back, including the service-assigned id and final status.
 */
public final class OmceTransaction {

    /** Applied and durable. The only status that permits an irreversible in-game side effect. */
    public static final String STATUS_COMMITTED = "committed";

    /** Accepted but not final; SUM must poll {@code /getTransaction} before acting. */
    public static final String STATUS_PENDING = "pending";

    public static final String STATUS_HELD = "held";
    public static final String STATUS_VOIDED = "voided";
    public static final String STATUS_REJECTED = "rejected";

    private final String transactionId;
    private final String status;
    private final String type;
    private final long amount;
    private final long feeAmount;
    @Nullable private final String idempotencyKey;
    private final boolean replayed;
    @Nullable private final String postedAt;

    public OmceTransaction(String transactionId, String status, String type, long amount,
        long feeAmount, @Nullable String idempotencyKey, boolean replayed,
        @Nullable String postedAt) {
        this.transactionId = transactionId == null ? "" : transactionId;
        this.status = (status == null || status.isEmpty()) ? STATUS_COMMITTED : status;
        this.type = type == null ? "" : type;
        this.amount = amount;
        this.feeAmount = feeAmount;
        this.idempotencyKey = idempotencyKey;
        this.replayed = replayed;
        this.postedAt = postedAt;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    /** Amount in minor units. Always non-negative; direction comes from the parties. */
    public long getAmount() {
        return amount;
    }

    public long getFeeAmount() {
        return feeAmount;
    }

    @Nullable
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /** True when the service returned a stored response for a repeated idempotency key. */
    public boolean isReplayed() {
        return replayed;
    }

    @Nullable
    public String getPostedAt() {
        return postedAt;
    }

    /**
     * True only for {@link #STATUS_COMMITTED}. Callers must gate every irreversible in-world
     * effect — handing over bills, transferring items, assigning plot ownership — on this.
     */
    public boolean isCommitted() {
        return STATUS_COMMITTED.equals(status);
    }

    /** True while the outcome is still unknown and the client should poll rather than act. */
    public boolean isPending() {
        return STATUS_PENDING.equals(status) || STATUS_HELD.equals(status);
    }

    @Override
    public String toString() {
        return "OmceTransaction{" + transactionId + " " + type + " " + amount + " " + status + "}";
    }
}
