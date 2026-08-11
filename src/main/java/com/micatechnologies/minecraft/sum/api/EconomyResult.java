package com.micatechnologies.minecraft.sum.api;

import java.util.OptionalDouble;
import javax.annotation.Nullable;

/**
 * The outcome of an economy operation.
 *
 * <p>Immutable. A successful result may carry the balance the operation left behind; a failed one
 * always carries an {@link EconomyFailure} and a player-facing message.
 *
 * <p><b>A failed result means nothing moved.</b> Every operation in this API is all-or-nothing: if
 * {@link #isOk()} is false, no wallet, bank account or escrow ticket was changed, and the caller
 * needs no compensating action.
 */
public final class EconomyResult {

    private final boolean ok;
    private final EconomyFailure failure;
    private final String message;
    private final double balance;
    private final boolean balanceKnown;

    private EconomyResult(boolean ok, EconomyFailure failure, String message, double balance,
            boolean balanceKnown) {
        this.ok = ok;
        this.failure = failure;
        this.message = message;
        this.balance = balance;
        this.balanceKnown = balanceKnown;
    }

    /** A success carrying no balance — used where the resulting balance is not meaningful. */
    public static EconomyResult ok() {
        return new EconomyResult(true, null, null, 0.0, false);
    }

    /**
     * A success carrying the balance the operation left behind.
     *
     * <p>A {@link Double#NaN} balance is recorded as unknown rather than propagated, so consumers
     * never have to guard against it.
     */
    public static EconomyResult ok(double balance) {
        boolean known = !Double.isNaN(balance);
        return new EconomyResult(true, null, null, known ? balance : 0.0, known);
    }

    /** A failure using the reason's default player-facing message. */
    public static EconomyResult fail(EconomyFailure failure) {
        // Resolve the message defensively: a null reason must reach the check below and be
        // reported as the misuse it is, not surface as an NPE from this line.
        return fail(failure, failure != null ? failure.getDefaultMessage() : null);
    }

    /** A failure with a message more specific than the reason's default. */
    public static EconomyResult fail(EconomyFailure failure, @Nullable String message) {
        if (failure == null) {
            throw new IllegalArgumentException("A failed EconomyResult needs a reason");
        }
        return new EconomyResult(false, failure,
                message != null ? message : failure.getDefaultMessage(), 0.0, false);
    }

    /** True when the operation completed and money moved as asked. */
    public boolean isOk() {
        return ok;
    }

    /** Why it failed. Null when {@link #isOk()}. */
    @Nullable
    public EconomyFailure getFailure() {
        return failure;
    }

    /** Player-facing text describing the failure. Null when {@link #isOk()}, never otherwise. */
    @Nullable
    public String getMessage() {
        return message;
    }

    /**
     * The balance after the operation, when the backend reported one.
     *
     * <p>Empty rather than {@link Double#NaN}: a remote bank may not have answered for this player
     * yet, and {@code NaN >= amount} is silently false in every comparison a caller would write.
     */
    public OptionalDouble getBalance() {
        return balanceKnown ? OptionalDouble.of(balance) : OptionalDouble.empty();
    }

    /** True when the failure is the calling mod's fault. See {@link EconomyFailure#isCallerError()}. */
    public boolean isCallerError() {
        return failure != null && failure.isCallerError();
    }

    @Override
    public String toString() {
        if (ok) {
            return balanceKnown ? "EconomyResult[ok, balance=" + balance + "]" : "EconomyResult[ok]";
        }
        return "EconomyResult[" + failure + ": " + message + "]";
    }
}
