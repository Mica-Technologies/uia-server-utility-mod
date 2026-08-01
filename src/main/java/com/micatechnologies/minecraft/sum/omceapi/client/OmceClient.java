package com.micatechnologies.minecraft.sum.omceapi.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.micatechnologies.minecraft.sum.omceapi.OmceAccount;
import com.micatechnologies.minecraft.sum.omceapi.OmceBalance;
import com.micatechnologies.minecraft.sum.omceapi.OmceEventPage;
import com.micatechnologies.minecraft.sum.omceapi.OmceHealth;
import com.micatechnologies.minecraft.sum.omceapi.OmceProtocol;
import com.micatechnologies.minecraft.sum.omceapi.OmceResult;
import com.micatechnologies.minecraft.sum.omceapi.OmceTransaction;
import com.micatechnologies.minecraft.sum.omceapi.OmceTransactionRequest;
import com.micatechnologies.minecraft.sum.omceapi.OmceTransactionResult;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSocketFactory;

/**
 * Typed access to every Open MCEconomic API endpoint. One method per endpoint, each returning an
 * {@link OmceResult} rather than throwing.
 *
 * <p>This is a pure protocol client: it holds no cache, schedules nothing, and knows nothing about
 * Minecraft. All of that lives in {@code omceapi.service}. That separation is what lets the
 * protocol be tested against a stub HTTP layer without a running server.
 *
 * <p><b>Every method blocks.</b> Call them from a background thread only.
 */
public final class OmceClient {

    private final OmceClientConfig config;
    private final OmceHttp http;

    /**
     * @param socketFactory from {@link OmceTlsProvider}; null uses the JDK default trust store.
     */
    public OmceClient(OmceClientConfig config, OmceEnvironment environment,
        @Nullable SSLSocketFactory socketFactory) {
        this.config = config;
        this.http = new OmceHttp(config, environment, socketFactory);
    }

    public OmceClientConfig getConfig() {
        return config;
    }

    // ---------------------------------------------------------------------------------------
    // Discovery
    // ---------------------------------------------------------------------------------------

    /** {@code GET /health} — liveness, currency, capabilities, and limits. */
    public OmceResult<OmceHealth> health() {
        OmceResult<JsonObject> raw = http.get(OmceProtocol.EP_HEALTH, null);
        if (raw.isFailure()) {
            return raw.propagateFailure();
        }
        OmceHealth health = OmceJson.readHealth(raw.get());
        return health == null
            ? OmceResult.fail(OmceProtocol.ERR_BAD_RESPONSE, "/health returned no usable body.", true)
            : OmceResult.ok(health);
    }

    /** {@code GET /getRequiredHeaders} — the service's header policy. Optional capability. */
    public OmceResult<List<String>> requiredHeaders() {
        OmceResult<JsonObject> raw = http.get(OmceProtocol.EP_REQUIRED_HEADERS, null);
        if (raw.isFailure()) {
            return raw.propagateFailure();
        }
        JsonArray headers = OmceJson.array(raw.get(), "headers");
        if (headers == null) {
            return OmceResult.ok(Collections.emptyList());
        }
        java.util.List<String> required = new java.util.ArrayList<>();
        for (com.google.gson.JsonElement e : headers) {
            if (e == null || !e.isJsonObject()) {
                continue;
            }
            JsonObject o = e.getAsJsonObject();
            if ("required".equalsIgnoreCase(OmceJson.string(o, "requirement", ""))) {
                String name = OmceJson.stringOrNull(o, "name");
                if (name != null && !name.isEmpty()) {
                    required.add(name);
                }
            }
        }
        return OmceResult.ok(required);
    }

    // ---------------------------------------------------------------------------------------
    // Accounts and balances
    // ---------------------------------------------------------------------------------------

    /**
     * {@code POST /resolveAccounts} — maps Minecraft UUIDs to opaque account ids.
     *
     * <p>A player the service cannot resolve is not an error: the returned list carries an entry
     * with {@code resolved=false} and a reason. The result fails only if the whole call did.
     */
    public OmceResult<List<OmceAccount>> resolveAccounts(Collection<UUID> playerUuids,
        @Nullable java.util.Map<UUID, String> namesByUuid, boolean createIfMissing) {
        if (playerUuids == null || playerUuids.isEmpty()) {
            return OmceResult.ok(Collections.emptyList());
        }
        JsonArray players = new JsonArray();
        for (UUID uuid : playerUuids) {
            if (uuid == null) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("playerUuid", uuid.toString().toLowerCase(Locale.ROOT));
            String name = namesByUuid == null ? null : namesByUuid.get(uuid);
            if (name != null && !name.isEmpty()) {
                entry.addProperty("playerName", name);
            }
            players.add(entry);
        }
        JsonObject body = new JsonObject();
        body.add("players", players);
        body.addProperty("createIfMissing", createIfMissing);

        OmceResult<JsonObject> raw = http.post(OmceProtocol.EP_RESOLVE_ACCOUNTS, body);
        return raw.isFailure() ? raw.propagateFailure() : OmceResult.ok(OmceJson.readAccounts(raw.get()));
    }

    /** {@code POST /getBalances} — the batch read that fills SUM's cache. */
    public OmceResult<List<OmceBalance>> getBalances(Collection<UUID> playerUuids) {
        if (playerUuids == null || playerUuids.isEmpty()) {
            return OmceResult.ok(Collections.emptyList());
        }
        JsonArray uuids = new JsonArray();
        for (UUID uuid : playerUuids) {
            if (uuid != null) {
                uuids.add(uuid.toString().toLowerCase(Locale.ROOT));
            }
        }
        JsonObject body = new JsonObject();
        body.add("playerUuids", uuids);

        OmceResult<JsonObject> raw = http.post(OmceProtocol.EP_GET_BALANCES, body);
        return raw.isFailure() ? raw.propagateFailure() : OmceResult.ok(OmceJson.readBalances(raw.get()));
    }

    /** {@code GET /getBalance} — single-account convenience read. Optional endpoint. */
    public OmceResult<OmceBalance> getBalance(UUID playerUuid) {
        OmceResult<JsonObject> raw =
            http.get(OmceProtocol.EP_GET_BALANCE, "playerUuid=" + encode(playerUuid.toString()));
        if (raw.isFailure()) {
            return raw.propagateFailure();
        }
        OmceBalance balance = OmceJson.readBalance(OmceJson.object(raw.get(), "balance"));
        return balance == null
            ? OmceResult.fail(OmceProtocol.ERR_BAD_RESPONSE, "/getBalance returned no balance.", false)
            : OmceResult.ok(balance);
    }

    // ---------------------------------------------------------------------------------------
    // Transactions
    // ---------------------------------------------------------------------------------------

    /**
     * {@code POST /processTransaction} — applies one movement of money atomically.
     *
     * <p>On success the result carries both the committed transaction and the post-transaction
     * balances of every settled party, so no follow-up balance read is needed.
     */
    public OmceResult<OmceTransactionResult> processTransaction(OmceTransactionRequest request,
        String currencyCode, @Nullable String occurredAt) {
        JsonObject body = request.toJson(currencyCode, config.getInstanceId(), occurredAt);
        OmceResult<JsonObject> raw = http.postMutating(OmceProtocol.EP_PROCESS_TRANSACTION, body,
            request.getIdempotencyKey(), initiatorOf(request));
        if (raw.isFailure()) {
            return raw.propagateFailure();
        }
        return toTransactionResult(raw.get(), "/processTransaction");
    }

    /**
     * {@code POST /validateTransaction} — a dry run. Optional capability.
     *
     * <p>Advisory only: the answer can be stale by the time the real call is made, so callers must
     * still handle a rejection from {@link #processTransaction}.
     *
     * @return ok(null) when the transaction would succeed; ok(error) describing why it would not.
     *     A failed result means the validation call itself failed.
     */
    public OmceResult<com.micatechnologies.minecraft.sum.omceapi.OmceError> validateTransaction(
        OmceTransactionRequest request, String currencyCode) {
        JsonObject body = request.toJson(currencyCode, config.getInstanceId(), null);
        OmceResult<JsonObject> raw = http.post(OmceProtocol.EP_VALIDATE_TRANSACTION, body);
        if (raw.isFailure()) {
            return raw.propagateFailure();
        }
        JsonObject envelope = raw.get();
        if (OmceJson.bool(envelope, "wouldSucceed", false)) {
            return OmceResult.ok(null);
        }
        JsonObject failure = OmceJson.object(envelope, "wouldFailWith");
        JsonObject wrapper = new JsonObject();
        wrapper.add("error", failure == null ? new JsonObject() : failure);
        return OmceResult.ok(OmceJson.readError(wrapper, 409));
    }

    /**
     * {@code GET /getTransaction} — the recovery path.
     *
     * <p>After a timeout, this is how SUM learns whether a player was actually charged. A
     * {@code TRANSACTION_NOT_FOUND} failure is <b>definitive</b>: the transaction was never
     * applied and the operation may be retried from scratch.
     */
    public OmceResult<OmceTransactionResult> getTransactionByKey(String idempotencyKey) {
        OmceResult<JsonObject> raw = http.get(OmceProtocol.EP_GET_TRANSACTION,
            "idempotencyKey=" + encode(idempotencyKey));
        if (raw.isFailure()) {
            return raw.propagateFailure();
        }
        return toTransactionResult(raw.get(), "/getTransaction");
    }

    /** {@code POST /voidTransaction} — reverses a committed transaction. Optional capability. */
    public OmceResult<OmceTransactionResult> voidTransaction(String transactionId, String reason,
        String idempotencyKey) {
        JsonObject body = new JsonObject();
        body.addProperty("idempotencyKey", idempotencyKey);
        body.addProperty("transactionId", transactionId);
        if (reason != null && !reason.isEmpty()) {
            body.addProperty("reason", reason);
        }
        body.addProperty("instance", config.getInstanceId());

        OmceResult<JsonObject> raw =
            http.postMutating(OmceProtocol.EP_VOID_TRANSACTION, body, idempotencyKey, null);
        if (raw.isFailure()) {
            // A void that reports ALREADY_VOIDED has achieved what the caller wanted, so treat it
            // as success — this is what makes a retried rollback converge instead of looping.
            com.micatechnologies.minecraft.sum.omceapi.OmceError error = raw.getError();
            if (error != null && OmceProtocol.ERR_ALREADY_VOIDED.equals(error.getCode())) {
                return OmceResult.ok(new OmceTransactionResult(
                    new OmceTransaction(transactionId, OmceTransaction.STATUS_VOIDED, "void", 0L, 0L,
                        idempotencyKey, true, null),
                    Collections.emptyList()));
            }
            return raw.propagateFailure();
        }
        return toTransactionResult(raw.get(), "/voidTransaction");
    }

    /** {@code POST /setBalance} — absolute set. Optional capability; requires an admin initiator. */
    public OmceResult<OmceTransactionResult> setBalance(UUID playerUuid, @Nullable String accountId,
        long balanceMinorUnits, String currencyCode, @Nullable Long expectedVersion,
        UUID adminUuid, @Nullable String adminName, @Nullable String reason, String idempotencyKey) {
        JsonObject target = new JsonObject();
        target.addProperty("type", com.micatechnologies.minecraft.sum.omceapi.OmceParty.TYPE_PLAYER);
        target.addProperty("playerUuid", playerUuid.toString().toLowerCase(Locale.ROOT));
        if (accountId != null && !accountId.isEmpty()) {
            target.addProperty("accountId", accountId);
        }

        JsonObject initiator = new JsonObject();
        if (adminUuid != null) {
            initiator.addProperty("playerUuid", adminUuid.toString().toLowerCase(Locale.ROOT));
        }
        if (adminName != null) {
            initiator.addProperty("playerName", adminName);
        }
        initiator.addProperty("role", OmceTransactionRequest.ROLE_ADMIN);

        JsonObject body = new JsonObject();
        body.addProperty("idempotencyKey", idempotencyKey);
        body.add("target", target);
        body.addProperty("balance", balanceMinorUnits);
        body.addProperty("currency", currencyCode);
        if (expectedVersion != null) {
            body.addProperty("expectedVersion", expectedVersion);
        }
        body.add("initiator", initiator);
        if (reason != null && !reason.isEmpty()) {
            body.addProperty("reason", reason);
        }
        body.addProperty("instance", config.getInstanceId());

        OmceResult<JsonObject> raw =
            http.postMutating(OmceProtocol.EP_SET_BALANCE, body, idempotencyKey, adminUuid);
        if (raw.isFailure()) {
            return raw.propagateFailure();
        }
        return toTransactionResult(raw.get(), "/setBalance");
    }

    // ---------------------------------------------------------------------------------------
    // Change feed
    // ---------------------------------------------------------------------------------------

    /**
     * {@code GET /getEvents} — the change feed carrying balance changes made outside Minecraft.
     *
     * @param cursor resume point, or null to start from now.
     */
    public OmceResult<OmceEventPage> getEvents(@Nullable String cursor, int limit) {
        StringBuilder query = new StringBuilder();
        if (cursor != null && !cursor.isEmpty()) {
            query.append("cursor=").append(encode(cursor)).append('&');
        }
        query.append("limit=").append(Math.max(1, Math.min(500, limit)));

        OmceResult<JsonObject> raw = http.get(OmceProtocol.EP_GET_EVENTS, query.toString());
        if (raw.isFailure()) {
            return raw.propagateFailure();
        }
        OmceEventPage page = OmceJson.readEvents(raw.get());
        return page == null
            ? OmceResult.fail(OmceProtocol.ERR_BAD_RESPONSE, "/getEvents returned no usable body.", true)
            : OmceResult.ok(page);
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private OmceResult<OmceTransactionResult> toTransactionResult(JsonObject envelope, String what) {
        OmceTransaction transaction = OmceJson.readTransaction(OmceJson.object(envelope, "transaction"));
        if (transaction == null) {
            return OmceResult.fail(OmceProtocol.ERR_BAD_RESPONSE,
                what + " succeeded but returned no transaction object.", false);
        }
        return OmceResult.ok(new OmceTransactionResult(transaction, OmceJson.readBalances(envelope)));
    }

    @Nullable
    private static UUID initiatorOf(OmceTransactionRequest request) {
        // Prefer the paying side for the initiator-state header — that's the player whose
        // game mode and permissions are relevant to whether the spend should be trusted.
        UUID source = request.getSource().getPlayerUuid();
        return source != null ? source : request.getDestination().getPlayerUuid();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is guaranteed present on every JVM.
            throw new IllegalStateException("UTF-8 is unavailable on this JVM.", e);
        }
    }
}
