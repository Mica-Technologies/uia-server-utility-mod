package com.micatechnologies.minecraft.sum.omceapi.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.micatechnologies.minecraft.sum.omceapi.OmceAccount;
import com.micatechnologies.minecraft.sum.omceapi.OmceBalance;
import com.micatechnologies.minecraft.sum.omceapi.OmceError;
import com.micatechnologies.minecraft.sum.omceapi.OmceEventPage;
import com.micatechnologies.minecraft.sum.omceapi.OmceHealth;
import com.micatechnologies.minecraft.sum.omceapi.OmceProtocol;
import com.micatechnologies.minecraft.sum.omceapi.OmceTransaction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Null-safe readers that turn service JSON into the model types.
 *
 * <p>Every accessor tolerates a missing, null, or wrong-typed field, because the protocol requires
 * clients to ignore what they don't recognise and a service is free to add fields. A malformed
 * <i>required</i> field is caught by the caller, which reports {@code BAD_RESPONSE} rather than
 * letting a {@code ClassCastException} escape onto a background thread.
 */
public final class OmceJson {

    private OmceJson() {}

    // ---------------------------------------------------------------------------------------
    // Primitive accessors
    // ---------------------------------------------------------------------------------------

    public static String string(@Nullable JsonObject o, String key, String fallback) {
        JsonElement e = element(o, key);
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return e.getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    @Nullable
    public static String stringOrNull(@Nullable JsonObject o, String key) {
        return string(o, key, null);
    }

    public static long number(@Nullable JsonObject o, String key, long fallback) {
        JsonElement e = element(o, key);
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return e.getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static int integer(@Nullable JsonObject o, String key, int fallback) {
        long v = number(o, key, fallback);
        if (v > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (v < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) v;
    }

    public static boolean bool(@Nullable JsonObject o, String key, boolean fallback) {
        JsonElement e = element(o, key);
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return e.getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    @Nullable
    public static JsonObject object(@Nullable JsonObject o, String key) {
        JsonElement e = element(o, key);
        return (e != null && e.isJsonObject()) ? e.getAsJsonObject() : null;
    }

    @Nullable
    public static JsonArray array(@Nullable JsonObject o, String key) {
        JsonElement e = element(o, key);
        return (e != null && e.isJsonArray()) ? e.getAsJsonArray() : null;
    }

    @Nullable
    private static JsonElement element(@Nullable JsonObject o, String key) {
        if (o == null || !o.has(key)) {
            return null;
        }
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? null : e;
    }

    /** Parses a canonical UUID string, returning null rather than throwing on garbage. */
    @Nullable
    public static UUID uuid(@Nullable JsonObject o, String key) {
        String raw = stringOrNull(o, key);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static List<String> stringList(@Nullable JsonObject o, String key) {
        JsonArray arr = array(o, key);
        if (arr == null) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(arr.size());
        for (JsonElement e : arr) {
            if (e != null && e.isJsonPrimitive()) {
                try {
                    out.add(e.getAsString());
                } catch (RuntimeException ignored) {
                    // skip an entry we can't read; the rest of the list is still useful
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------
    // Model readers
    // ---------------------------------------------------------------------------------------

    /** Reads the {@code error} object from a failure envelope. Never returns null. */
    public static OmceError readError(@Nullable JsonObject envelope, int httpStatus) {
        JsonObject err = object(envelope, "error");
        if (err == null) {
            return new OmceError(codeForStatus(httpStatus),
                "Service returned HTTP " + httpStatus + " with no error object.",
                httpStatus >= 500 || httpStatus == 429, 0L, null);
        }
        String code = string(err, "code", codeForStatus(httpStatus));
        // retryable is required by the spec, but fall back to the code's default rather than
        // assuming false, which would turn a transient outage into a hard failure.
        boolean retryable = bool(err, "retryable", OmceProtocol.isRetryableByDefault(code));
        return new OmceError(code, string(err, "message", ""), retryable,
            number(err, "retryAfterMs", 0L), object(err, "details"));
    }

    private static String codeForStatus(int status) {
        switch (status) {
            case 401:
                return OmceProtocol.ERR_UNAUTHENTICATED;
            case 403:
                return OmceProtocol.ERR_FORBIDDEN;
            case 404:
                return OmceProtocol.ERR_UNKNOWN_ACCOUNT;
            case 429:
                return OmceProtocol.ERR_RATE_LIMITED;
            case 503:
                return OmceProtocol.ERR_SERVICE_UNAVAILABLE;
            default:
                return status >= 500 ? OmceProtocol.ERR_INTERNAL_ERROR : OmceProtocol.ERR_MALFORMED_REQUEST;
        }
    }

    @Nullable
    public static OmceBalance readBalance(@Nullable JsonObject o) {
        if (o == null) {
            return null;
        }
        long balance = number(o, "balance", Long.MIN_VALUE);
        if (balance == Long.MIN_VALUE) {
            return null;
        }
        return new OmceBalance(
            string(o, "accountId", ""),
            uuid(o, "playerUuid"),
            balance,
            number(o, "available", balance),
            number(o, "held", 0L),
            number(o, "version", 0L),
            string(o, "status", OmceProtocol.ACCOUNT_ACTIVE));
    }

    public static List<OmceBalance> readBalances(@Nullable JsonObject envelope) {
        JsonArray arr = array(envelope, "balances");
        if (arr == null) {
            return Collections.emptyList();
        }
        List<OmceBalance> out = new ArrayList<>(arr.size());
        for (JsonElement e : arr) {
            if (e != null && e.isJsonObject()) {
                OmceBalance b = readBalance(e.getAsJsonObject());
                if (b != null) {
                    out.add(b);
                }
            }
        }
        return out;
    }

    @Nullable
    public static OmceTransaction readTransaction(@Nullable JsonObject o) {
        if (o == null) {
            return null;
        }
        JsonObject fee = object(o, "fee");
        return new OmceTransaction(
            string(o, "transactionId", ""),
            string(o, "status", OmceTransaction.STATUS_COMMITTED),
            string(o, "type", ""),
            number(o, "amount", 0L),
            fee == null ? 0L : number(fee, "amount", 0L),
            stringOrNull(o, "idempotencyKey"),
            bool(o, "replayed", false),
            stringOrNull(o, "postedAt"));
    }

    public static List<OmceAccount> readAccounts(@Nullable JsonObject envelope) {
        JsonArray arr = array(envelope, "accounts");
        if (arr == null) {
            return Collections.emptyList();
        }
        List<OmceAccount> out = new ArrayList<>(arr.size());
        for (JsonElement e : arr) {
            if (e == null || !e.isJsonObject()) {
                continue;
            }
            JsonObject o = e.getAsJsonObject();
            UUID uuid = uuid(o, "playerUuid");
            if (uuid == null) {
                continue;
            }
            out.add(new OmceAccount(uuid, bool(o, "resolved", false), stringOrNull(o, "accountId"),
                stringOrNull(o, "displayName"), stringOrNull(o, "status"), stringOrNull(o, "reason"),
                stringOrNull(o, "linkInstructions")));
        }
        return out;
    }

    @Nullable
    public static OmceHealth readHealth(@Nullable JsonObject envelope) {
        if (envelope == null) {
            return null;
        }
        JsonObject cur = object(envelope, "currency");
        OmceHealth.Currency currency = new OmceHealth.Currency(
            string(cur, "code", "SUM"),
            string(cur, "symbol", "$"),
            integer(cur, "minorUnitDigits", 2));

        JsonObject caps = object(envelope, "capabilities");
        Set<String> enabled = new LinkedHashSet<>();
        if (caps != null) {
            for (java.util.Map.Entry<String, JsonElement> entry : caps.entrySet()) {
                JsonElement v = entry.getValue();
                if (v != null && v.isJsonPrimitive()) {
                    try {
                        if (v.getAsBoolean()) {
                            enabled.add(entry.getKey());
                        }
                    } catch (RuntimeException ignored) {
                        // non-boolean capability value; treat as absent
                    }
                }
            }
        }

        JsonObject lim = object(envelope, "limits");
        OmceHealth.Limits limits = new OmceHealth.Limits(
            integer(lim, "maxBatchAccounts", 50),
            number(lim, "maxTransactionAmount", 0L),
            integer(lim, "requestsPerMinute", 0),
            integer(lim, "idempotencyRetentionHours", 24));

        Set<String> settled = new LinkedHashSet<>(stringList(envelope, "settledPartyTypes"));
        if (settled.isEmpty()) {
            settled.add(com.micatechnologies.minecraft.sum.omceapi.OmceParty.TYPE_PLAYER);
        }

        JsonObject impl = object(envelope, "implementation");
        String implName = string(impl, "name", "unknown")
            + (impl != null && impl.has("version") ? " " + string(impl, "version", "") : "");

        return new OmceHealth(string(envelope, "status", OmceProtocol.STATUS_OK), implName.trim(),
            currency, new OmceHealth.Capabilities(enabled), settled, limits,
            stringList(envelope, "requiredHeaders"));
    }

    @Nullable
    public static OmceEventPage readEvents(@Nullable JsonObject envelope) {
        if (envelope == null) {
            return null;
        }
        JsonArray arr = array(envelope, "events");
        List<OmceEventPage.Event> out = new ArrayList<>();
        if (arr != null) {
            for (JsonElement e : arr) {
                if (e == null || !e.isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getAsJsonObject();
                // The balance fields sit on the event itself, not in a nested object.
                OmceBalance balance = readBalance(o);
                JsonObject tx = object(o, "transaction");
                out.add(new OmceEventPage.Event(
                    string(o, "cursor", ""),
                    string(o, "kind", ""),
                    uuid(o, "playerUuid"),
                    balance,
                    tx == null ? null : stringOrNull(tx, "reason")));
            }
        }
        return new OmceEventPage(out, stringOrNull(envelope, "nextCursor"),
            bool(envelope, "hasMore", false));
    }
}
