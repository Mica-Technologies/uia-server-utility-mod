package com.micatechnologies.minecraft.sum.api;

/**
 * Why an economy operation did not happen.
 *
 * <p>SUM's internal economy methods return a bare {@code boolean}, which tells a caller nothing
 * about whether the player was broke, the mod lacked permission, or the backend was down — three
 * situations that want three different responses. Every {@link EconomyResult} carries one of these
 * instead.
 *
 * <p>The split that matters most is {@link #isCallerError()}: some failures are the integrating
 * mod's fault and should be logged for a developer to fix, while the rest are ordinary situations
 * a player should simply be told about.
 */
public enum EconomyFailure {

    /** The mod is not listed in {@code economy_integration.allowedMods}. */
    NOT_AUTHORIZED("That feature is not authorized to use the economy.", true),

    /** The mod is authorized but was not granted the scope this call requires. */
    MISSING_SCOPE("That feature is not permitted to do that with the economy.", true),

    /** No economy backend is attached, or the bank is unreachable and policy forbids proceeding. */
    ECONOMY_UNAVAILABLE("The economy is unavailable right now.", false),

    /** Called off the server thread. The operation was refused rather than risk a data race. */
    WRONG_THREAD("An internal error prevented that transaction.", true),

    /** Called on a client. Economy mutations are server-side only. */
    CLIENT_SIDE("An internal error prevented that transaction.", true),

    /** The amount was negative, infinite, or not a number. */
    INVALID_AMOUNT("That amount is not valid.", true),

    /** The amount exceeded the per-call cap this server configures for integrating mods. */
    AMOUNT_TOO_LARGE("That amount is larger than this server allows.", false),

    /** The player does not have the money. */
    INSUFFICIENT_FUNDS("You cannot afford that.", false),

    /** The economy's smallest unit cannot express the amount exactly. Quantise first. */
    NOT_REPRESENTABLE("That amount can't be represented by the economy.", true),

    /** The backend understood the request and declined it. */
    BACKEND_REFUSED("The economy refused that transaction.", false),

    /** The backend could not be reached, or answered with an error. */
    BACKEND_ERROR("The economy could not be reached. Please try again.", false),

    /** No open escrow ticket with that id, or it belongs to a different mod. */
    ESCROW_NOT_FOUND("That held amount no longer exists.", true),

    /** The ticket was already released or refunded. */
    ESCROW_ALREADY_CLOSED("That held amount has already been settled.", true),

    /** Escrow can only be released to an online player. */
    RECIPIENT_OFFLINE("That player is not online.", false);

    private final String defaultMessage;
    private final boolean callerError;

    EconomyFailure(String defaultMessage, boolean callerError) {
        this.defaultMessage = defaultMessage;
        this.callerError = callerError;
    }

    /**
     * A player-facing sentence describing the failure, safe to show verbatim.
     *
     * <p>{@link EconomyResult#getMessage()} may carry something more specific; prefer that and fall
     * back to this.
     */
    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * True when the failure indicates a bug in the calling mod rather than a situation the player
     * caused.
     *
     * <p>Log these; a player can do nothing about a missing scope or an off-thread call, and
     * showing them "an internal error occurred" repeatedly is how a broken integration goes
     * unnoticed for a month.
     */
    public boolean isCallerError() {
        return callerError;
    }
}
