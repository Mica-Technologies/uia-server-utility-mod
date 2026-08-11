package com.micatechnologies.minecraft.sum.economy;

import javax.annotation.Nullable;

/**
 * Who is moving money right now.
 *
 * <p>Every wallet and bank movement is posted as an event, and a listener's first question is
 * always "who did this?". The trouble is that an integrating mod's spend goes through the same
 * {@link WalletService#spend} as a shop purchase, so by the time the event is posted the caller's
 * identity is long gone from the stack.
 *
 * <p>This carries it. {@link com.micatechnologies.minecraft.sum.economy.apiimpl.EconomyHandleImpl}
 * wraps its calls in a scope naming the mod; everything else runs at the default, {@link #SUM}.
 *
 * <p><b>A plain static is enough.</b> Every mutating economy path is server-thread only — the
 * wallet mutates inventory and capabilities with no locking, and the API refuses off-thread calls
 * outright — so there is exactly one thread that can be inside a scope. Scopes save and restore
 * the previous value rather than resetting to a default, so nesting (escrow opening a hold, which
 * spends a wallet) attributes correctly.
 */
public final class EconomyAttribution {

    /** Attribution for SUM's own features: shops, plots, jobs, loyalty, {@code /pay}, the ATM. */
    public static final String SUM = "sum";

    private static String currentModId = SUM;
    private static String currentReason = null;

    private EconomyAttribution() {}

    /** The mod behind the movement being made right now. Never null. */
    public static String currentModId() {
        return currentModId;
    }

    /** The reason given for it, if the caller supplied one. */
    @Nullable
    public static String currentReason() {
        return currentReason;
    }

    /**
     * Attributes everything done inside the returned scope to {@code modId}.
     *
     * <p>Use with try-with-resources so the previous attribution is restored even if the body
     * throws — a leaked scope would silently mislabel every later transaction on the server.
     */
    public static Scope enter(String modId, @Nullable String reason) {
        Scope scope = new Scope(currentModId, currentReason);
        currentModId = modId == null ? SUM : modId;
        currentReason = reason;
        return scope;
    }

    /** Restores the attribution that was in force before it was created. */
    public static final class Scope implements AutoCloseable {

        private final String previousModId;
        private final String previousReason;

        private Scope(String previousModId, String previousReason) {
            this.previousModId = previousModId;
            this.previousReason = previousReason;
        }

        @Override
        public void close() {
            currentModId = previousModId;
            currentReason = previousReason;
        }
    }
}
