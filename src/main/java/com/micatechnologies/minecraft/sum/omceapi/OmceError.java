package com.micatechnologies.minecraft.sum.omceapi;

import com.google.gson.JsonObject;
import javax.annotation.Nullable;

/**
 * The {@code error} object from a failed response, or a locally synthesised equivalent when a
 * request never reached the service.
 *
 * <p>{@link #code} drives client behaviour; {@link #message} is diagnostic text for the server log
 * and is never shown to a player verbatim. {@link #playerMessage()} produces the player-facing
 * wording instead.
 */
public final class OmceError {

    private final String code;
    private final String message;
    private final boolean retryable;
    private final long retryAfterMs;
    @Nullable private final JsonObject details;

    public OmceError(String code, String message, boolean retryable, long retryAfterMs,
        @Nullable JsonObject details) {
        this.code = (code == null || code.isEmpty()) ? OmceProtocol.ERR_INTERNAL_ERROR : code;
        this.message = (message == null) ? "" : message;
        this.retryable = retryable;
        this.retryAfterMs = Math.max(0L, retryAfterMs);
        this.details = details;
    }

    /** Builds a client-side error for a failure that never reached the service. */
    public static OmceError local(String code, String message, boolean retryable) {
        return new OmceError(code, message, retryable, 0L, null);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /** Whether retrying the identical request (same idempotency key) could succeed. */
    public boolean isRetryable() {
        return retryable;
    }

    /** Minimum wait before a retry, in milliseconds; 0 when the service gave no hint. */
    public long getRetryAfterMs() {
        return retryAfterMs;
    }

    @Nullable
    public JsonObject getDetails() {
        return details;
    }

    /** @return a numeric field from {@code details}, or {@code fallback} if absent. */
    public long detailLong(String key, long fallback) {
        if (details == null || !details.has(key) || details.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return details.get(key).getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    /** @return a string field from {@code details}, or null if absent. */
    @Nullable
    public String detailString(String key) {
        if (details == null || !details.has(key) || details.get(key).isJsonNull()) {
            return null;
        }
        try {
            return details.get(key).getAsString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Player-facing wording for this error. Chat should never carry the raw {@link #message},
     * which may name internal service details; this maps the code to something a player can act
     * on, and stays deliberately vague about anything that is not their problem.
     *
     * @param digits currency scale, for rendering amounts inside the message.
     * @param symbol currency symbol, for the same.
     */
    public String playerMessage(int digits, String symbol) {
        switch (code) {
            case OmceProtocol.ERR_INSUFFICIENT_FUNDS: {
                long shortfall = detailLong("shortfall", -1L);
                if (shortfall >= 0L) {
                    return "Insufficient funds — you need "
                        + OmceMoney.format(shortfall, digits, symbol) + " more.";
                }
                return "Insufficient funds.";
            }
            case OmceProtocol.ERR_ACCOUNT_NOT_LINKED: {
                String hint = detailString("linkInstructions");
                return (hint != null && !hint.isEmpty())
                    ? hint
                    : "Your account isn't linked to the economy yet.";
            }
            case OmceProtocol.ERR_ACCOUNT_FROZEN:
                return "Your account is frozen. Contact an administrator.";
            case OmceProtocol.ERR_UNKNOWN_ACCOUNT:
                return "You don't have an economy account yet.";
            case OmceProtocol.ERR_LIMIT_EXCEEDED: {
                long wait = getRetryAfterMs();
                return wait > 0L
                    ? "That exceeds a spending limit. Try again in " + describeWait(wait) + "."
                    : "That exceeds a spending limit.";
            }
            case OmceProtocol.ERR_AMOUNT_INVALID:
                return "That amount isn't valid.";
            case OmceProtocol.ERR_ECONOMY_READ_ONLY:
                return "The economy is in maintenance — spending is temporarily disabled.";
            case OmceProtocol.ERR_RATE_LIMITED:
                return "The economy is busy right now. Please try again shortly.";
            case OmceProtocol.ERR_NOT_CONNECTED:
            case OmceProtocol.ERR_SERVICE_UNAVAILABLE:
            case OmceProtocol.ERR_TRANSPORT:
                return "The economy service is unreachable. Please try again shortly.";
            case OmceProtocol.ERR_ENVIRONMENT_REJECTED:
                return "This world isn't permitted to use the economy.";
            default:
                // Everything else is an operator problem (auth, protocol, bad request). The player
                // can do nothing about it, so say so without leaking specifics.
                return "The economy request failed. Please tell an administrator.";
        }
    }

    private static String describeWait(long ms) {
        long seconds = (ms + 999L) / 1000L;
        if (seconds < 60L) {
            return seconds + "s";
        }
        return (seconds / 60L) + "m";
    }

    @Override
    public String toString() {
        return code + (message.isEmpty() ? "" : ": " + message);
    }
}
