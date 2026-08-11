package com.micatechnologies.minecraft.sum.api;

import java.util.Optional;
import javax.annotation.Nullable;

/**
 * The outcome of opening an escrow ticket: an {@link EconomyResult} plus the ticket itself.
 *
 * <p>On success the ticket is always present, and the player's wallet has already been debited. On
 * failure nothing was debited and there is no ticket to clean up.
 */
public final class EscrowResult {

    private final EconomyResult result;
    private final EscrowTicket ticket;

    private EscrowResult(EconomyResult result, EscrowTicket ticket) {
        this.result = result;
        this.ticket = ticket;
    }

    /** A success. The ticket is required — an open with no ticket is a contradiction. */
    public static EscrowResult ok(EscrowTicket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("A successful EscrowResult needs a ticket");
        }
        return new EscrowResult(EconomyResult.ok(ticket.getAmount()), ticket);
    }

    /** A failure using the reason's default player-facing message. */
    public static EscrowResult fail(EconomyFailure failure) {
        return new EscrowResult(EconomyResult.fail(failure), null);
    }

    /** A failure with a message more specific than the reason's default. */
    public static EscrowResult fail(EconomyFailure failure, @Nullable String message) {
        return new EscrowResult(EconomyResult.fail(failure, message), null);
    }

    /** True when the ticket was opened and the wallet debited. */
    public boolean isOk() {
        return result.isOk();
    }

    /** Why it failed. Null when {@link #isOk()}. */
    @Nullable
    public EconomyFailure getFailure() {
        return result.getFailure();
    }

    /** Player-facing text describing the failure. Null when {@link #isOk()}. */
    @Nullable
    public String getMessage() {
        return result.getMessage();
    }

    /** The ticket. Present exactly when {@link #isOk()}. */
    public Optional<EscrowTicket> getTicket() {
        return Optional.ofNullable(ticket);
    }

    /** The same outcome as a plain {@link EconomyResult}, for uniform error handling. */
    public EconomyResult toResult() {
        return result;
    }

    @Override
    public String toString() {
        return isOk() ? "EscrowResult[ok, " + ticket + "]" : "EscrowResult[" + result + "]";
    }
}
