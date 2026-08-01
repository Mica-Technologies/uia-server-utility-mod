package com.micatechnologies.minecraft.sum.omceapi;

import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * The outcome of any API call: either a value or an {@link OmceError}, never both and never
 * neither.
 *
 * <p>Used instead of throwing because every call in this client runs on a background executor
 * where an escaped exception is easy to lose, and because a failed economy call is an expected
 * outcome the caller must handle, not an exceptional one.
 *
 * @param <T> the success payload type
 */
public final class OmceResult<T> {

    @Nullable private final T value;
    @Nullable private final OmceError error;

    private OmceResult(@Nullable T value, @Nullable OmceError error) {
        this.value = value;
        this.error = error;
    }

    public static <T> OmceResult<T> ok(T value) {
        return new OmceResult<>(value, null);
    }

    public static <T> OmceResult<T> fail(OmceError error) {
        return new OmceResult<>(null, error == null
            ? OmceError.local(OmceProtocol.ERR_INTERNAL_ERROR, "Unspecified failure.", false)
            : error);
    }

    /** Convenience for a locally generated failure. */
    public static <T> OmceResult<T> fail(String code, String message, boolean retryable) {
        return fail(OmceError.local(code, message, retryable));
    }

    /** Re-wraps this failure as a different payload type. Illegal on a success. */
    public <R> OmceResult<R> propagateFailure() {
        if (error == null) {
            throw new IllegalStateException("propagateFailure() called on a successful result");
        }
        return fail(error);
    }

    public boolean isOk() {
        return error == null;
    }

    public boolean isFailure() {
        return error != null;
    }

    /** @throws IllegalStateException if this is a failure. Check {@link #isOk()} first. */
    public T get() {
        if (error != null) {
            throw new IllegalStateException("get() on a failed result: " + error);
        }
        return value;
    }

    /** @return the value, or {@code fallback} if this is a failure. */
    public T orElse(T fallback) {
        return error == null ? value : fallback;
    }

    @Nullable
    public OmceError getError() {
        return error;
    }

    /** Maps the success value, leaving a failure untouched. */
    public <R> OmceResult<R> map(Function<T, R> mapper) {
        return error != null ? propagateFailure() : ok(mapper.apply(value));
    }

    @Override
    public String toString() {
        return error == null ? "ok(" + value + ")" : "fail(" + error + ")";
    }
}
