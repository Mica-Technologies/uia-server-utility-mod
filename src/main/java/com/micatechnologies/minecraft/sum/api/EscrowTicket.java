package com.micatechnologies.minecraft.sum.api;

import java.util.UUID;

/**
 * A claim on money held by the server on a mod's behalf.
 *
 * <p>Opening a ticket debits the player's wallet immediately; the money then belongs to no one
 * until the ticket is {@linkplain EconomyHandle#escrowRelease released} to a recipient or
 * {@linkplain EconomyHandle#escrowRefund refunded} to the player who put it up. This is the
 * primitive behind a wager: the stake is gone from the player's pocket the moment they commit it,
 * so it cannot be spent twice while a round resolves, and it survives a server restart.
 *
 * <p><b>Escrow holds value, it never creates it.</b> Releasing a ticket moves exactly the amount
 * that was held. A payout larger than the stake is a release <i>plus</i> a separate
 * {@link EconomyHandle#walletCredit walletCredit} for the winnings — there is deliberately no way
 * to release more than went in.
 *
 * <p>Immutable. Hold the {@link #getId() id} if you need to persist a reference across a restart;
 * re-obtain the ticket from {@link EconomyHandle#listOpenEscrows()} rather than serialising this
 * object yourself.
 */
public final class EscrowTicket {

    private final UUID id;
    private final UUID owner;
    private final double amount;
    private final String owningModId;
    private final String reason;
    private final long openedAtMillis;

    public EscrowTicket(UUID id, UUID owner, double amount, String owningModId, String reason,
            long openedAtMillis) {
        this.id = id;
        this.owner = owner;
        this.amount = amount;
        this.owningModId = owningModId;
        this.reason = reason;
        this.openedAtMillis = openedAtMillis;
    }

    /** Stable identifier for this ticket, unique for the life of the world. */
    public UUID getId() {
        return id;
    }

    /** The player whose wallet was debited, and who a refund goes back to. */
    public UUID getOwner() {
        return owner;
    }

    /** The amount held, in dollars. Fixed at open time. */
    public double getAmount() {
        return amount;
    }

    /** The mod that opened the ticket. Only that mod may release or refund it. */
    public String getOwningModId() {
        return owningModId;
    }

    /** The reason given at open time, as recorded in logs and the remote ledger. */
    public String getReason() {
        return reason;
    }

    /** When the ticket was opened, as epoch milliseconds. Used by the orphan sweep. */
    public long getOpenedAtMillis() {
        return openedAtMillis;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EscrowTicket)) {
            return false;
        }
        return id.equals(((EscrowTicket) other).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "EscrowTicket[" + id + ", " + owningModId + ", " + amount + ", owner=" + owner + "]";
    }
}
