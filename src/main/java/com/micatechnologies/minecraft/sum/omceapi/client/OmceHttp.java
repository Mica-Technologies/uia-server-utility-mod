package com.micatechnologies.minecraft.sum.omceapi.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.omceapi.OmceError;
import com.micatechnologies.minecraft.sum.omceapi.OmceProtocol;
import com.micatechnologies.minecraft.sum.omceapi.OmceResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/**
 * Low-level HTTP for the economy API: builds requests, applies auth and signing, executes with
 * retry, and returns a parsed JSON envelope.
 *
 * <p><b>This class must never be called from the Minecraft server thread.</b> Every method here
 * blocks on network I/O; a 200 ms call on the tick loop is a four-tick freeze for every player.
 * {@code OmceEconomyService} owns the executor that keeps that promise.
 *
 * <p>Retries reuse the caller's idempotency key unchanged, which is what makes a retried payment
 * safe: the service recognises the repeat and returns its stored response instead of charging
 * twice.
 */
public final class OmceHttp {

    /** Cap on a response body we will buffer, so a broken service can't exhaust the heap. */
    private static final int MAX_RESPONSE_BYTES = 1 << 20;

    private final OmceClientConfig config;
    private final OmceEnvironment environment;
    @Nullable private final OmceSigner signer;
    @Nullable private final SSLSocketFactory socketFactory;
    private final OmceBackoff backoff = new OmceBackoff();

    /** Set once a deprecation warning has been logged, so it appears per server start, not per call. */
    private final AtomicBoolean deprecationLogged = new AtomicBoolean(false);

    public OmceHttp(OmceClientConfig config, OmceEnvironment environment,
        @Nullable SSLSocketFactory socketFactory) {
        this.config = config;
        this.environment = environment;
        this.socketFactory = socketFactory;
        this.signer = config.isHmacEnabled() ? new OmceSigner(config.getHmacSecret()) : null;
    }

    /** A GET with no body. */
    public OmceResult<JsonObject> get(String path, @Nullable String query) {
        return execute("GET", path, query, null, null, null);
    }

    /** A POST whose body is a safe read (no idempotency key). */
    public OmceResult<JsonObject> post(String path, JsonObject body) {
        return execute("POST", path, null, body, null, null);
    }

    /**
     * A POST that mutates state.
     *
     * @param idempotencyKey stable across every retry of one logical operation.
     * @param initiator acting player, for the initiator-state integrity header.
     */
    public OmceResult<JsonObject> postMutating(String path, JsonObject body, String idempotencyKey,
        @Nullable UUID initiator) {
        return execute("POST", path, null, body, idempotencyKey, initiator);
    }

    private OmceResult<JsonObject> execute(String method, String path, @Nullable String query,
        @Nullable JsonObject body, @Nullable String idempotencyKey, @Nullable UUID initiator) {
        byte[] payload = body == null ? null : body.toString().getBytes(StandardCharsets.UTF_8);
        String pathWithQuery = OmceProtocol.BASE_PATH + path + (query == null ? "" : "?" + query);
        String url = config.getBaseUrl() + pathWithQuery;

        OmceError lastError = null;
        int attempts = config.getMaxRetries() + 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            OmceResult<JsonObject> result =
                attempt(method, url, pathWithQuery, payload, idempotencyKey, initiator);
            if (result.isOk()) {
                return result;
            }
            lastError = result.getError();
            if (lastError == null || !lastError.isRetryable() || attempt == attempts) {
                break;
            }
            long delay = backoff.delayFor(attempt, lastError.getRetryAfterMs());
            if (config.isVerboseLogging()) {
                Sum.LOGGER.info("[omce] {} {} failed with {} — retry {}/{} in {}ms",
                    method, path, lastError.getCode(), attempt, config.getMaxRetries(), delay);
            }
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return OmceResult.fail(OmceProtocol.ERR_TRANSPORT,
                    "Interrupted while backing off before a retry.", false);
            }
        }
        return OmceResult.fail(lastError);
    }

    private OmceResult<JsonObject> attempt(String method, String url, String pathWithQuery,
        @Nullable byte[] payload, @Nullable String idempotencyKey, @Nullable UUID initiator) {
        HttpURLConnection connection = null;
        try {
            connection = open(url);
            connection.setRequestMethod(method);
            connection.setConnectTimeout(config.getConnectTimeoutMs());
            connection.setReadTimeout(config.getReadTimeoutMs());
            connection.setInstanceFollowRedirects(false);
            applyHeaders(connection, method, pathWithQuery, payload, idempotencyKey, initiator);

            if (payload != null) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload);
                }
            }

            int status = connection.getResponseCode();
            String raw = readBody(connection, status);
            noteDeprecation(connection, pathWithQuery);

            if (status >= 300 && status < 400) {
                // Never follow a redirect: a downgrade to http:// would put the bearer token on
                // the wire in plaintext, and a cross-host redirect would send it somewhere the
                // operator never configured.
                return OmceResult.fail(OmceProtocol.ERR_TRANSPORT,
                    "Service returned redirect " + status + " to '"
                        + String.valueOf(connection.getHeaderField("Location"))
                        + "'; redirects are refused. Point economy_api.baseUrl at the final URL.",
                    false);
            }

            JsonObject envelope = parse(raw);
            if (envelope == null) {
                return OmceResult.fail(OmceProtocol.ERR_BAD_RESPONSE,
                    "Service returned HTTP " + status + " with a body that is not a JSON object.",
                    status >= 500);
            }
            if (status >= 200 && status < 300 && OmceJson.bool(envelope, "ok", false)) {
                return OmceResult.ok(envelope);
            }
            return OmceResult.fail(OmceJson.readError(envelope, status));

        } catch (java.net.SocketTimeoutException e) {
            // Retryable, and — critically — an unknown outcome for a mutating call. The caller
            // recovers via /getTransaction rather than assuming the request never landed.
            return OmceResult.fail(OmceProtocol.ERR_TRANSPORT,
                "Timed out talking to the economy service: " + e.getMessage(), true);
        } catch (javax.net.ssl.SSLException e) {
            // Not retryable: a handshake failure is a configuration problem (wrong certificate,
            // expired, hostname mismatch) that retrying cannot fix.
            return OmceResult.fail(OmceProtocol.ERR_TRANSPORT,
                "TLS failure talking to the economy service: " + e.getMessage()
                    + " — check economy_api.certificatePath and the service's certificate.", false);
        } catch (IOException e) {
            return OmceResult.fail(OmceProtocol.ERR_TRANSPORT,
                "Could not reach the economy service: " + e.getMessage(), true);
        } catch (RuntimeException e) {
            return OmceResult.fail(OmceProtocol.ERR_BAD_RESPONSE,
                "Unexpected client failure: " + e, false);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        if (connection instanceof HttpsURLConnection && socketFactory != null) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(socketFactory);
        }
        return connection;
    }

    private void applyHeaders(HttpURLConnection connection, String method, String pathWithQuery,
        @Nullable byte[] payload, @Nullable String idempotencyKey, @Nullable UUID initiator) {
        connection.setRequestProperty(OmceProtocol.HEADER_AUTHORIZATION,
            "Bearer " + config.getAuthToken());
        connection.setRequestProperty(OmceProtocol.HEADER_ACCEPT, "application/json");
        connection.setRequestProperty(OmceProtocol.HEADER_USER_AGENT, config.getUserAgent());
        connection.setRequestProperty(OmceProtocol.HEADER_PROTOCOL_VERSION, OmceProtocol.VERSION);
        connection.setRequestProperty(OmceProtocol.HEADER_INSTANCE, config.getInstanceId());
        // A fresh id per HTTP attempt, unlike the idempotency key, so service logs can tell
        // retries apart while still recognising them as one operation.
        connection.setRequestProperty(OmceProtocol.HEADER_REQUEST_ID, UUID.randomUUID().toString());

        if (payload != null) {
            connection.setRequestProperty(OmceProtocol.HEADER_CONTENT_TYPE,
                OmceProtocol.CONTENT_TYPE_JSON);
        }
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            connection.setRequestProperty(OmceProtocol.HEADER_IDEMPOTENCY_KEY, idempotencyKey);
        }
        for (Map.Entry<String, String> e
            : environment.buildHeaders(config.isSendIntegrityHeaders(), initiator).entrySet()) {
            connection.setRequestProperty(e.getKey(), e.getValue());
        }
        if (signer != null) {
            long timestamp = System.currentTimeMillis();
            connection.setRequestProperty(OmceProtocol.HEADER_TIMESTAMP, Long.toString(timestamp));
            connection.setRequestProperty(OmceProtocol.HEADER_SIGNATURE,
                signer.sign(method, pathWithQuery, timestamp, payload));
        }
    }

    /** Reads the body from whichever stream applies, capped and never throwing. */
    private String readBody(HttpURLConnection connection, int status) {
        InputStream in = null;
        try {
            in = (status >= 400) ? connection.getErrorStream() : connection.getInputStream();
            if (in == null) {
                return "";
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(8192, MAX_RESPONSE_BYTES));
            byte[] chunk = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(chunk)) > 0) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    Sum.LOGGER.warn("[omce] Response body exceeded {} bytes; truncating.",
                        MAX_RESPONSE_BYTES);
                    break;
                }
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // nothing useful to do; the connection is being discarded anyway
                }
            }
        }
    }

    @Nullable
    private JsonObject parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            com.google.gson.JsonElement parsed = new JsonParser().parse(raw);
            if (config.isVerboseLogging()) {
                Sum.LOGGER.info("[omce] <- {}", raw.length() > 2000 ? raw.substring(0, 2000) + "…" : raw);
            }
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Logs an RFC 8594 deprecation notice once per server start rather than per request. */
    private void noteDeprecation(HttpURLConnection connection, String path) {
        String deprecation = connection.getHeaderField(OmceProtocol.HEADER_DEPRECATION);
        if (deprecation == null || deprecation.isEmpty()) {
            return;
        }
        if (deprecationLogged.compareAndSet(false, true)) {
            String sunset = connection.getHeaderField(OmceProtocol.HEADER_SUNSET);
            Sum.LOGGER.warn("[omce] The economy service reports {} as deprecated ({}){}",
                path, deprecation,
                (sunset == null || sunset.isEmpty()) ? "" : "; stops working after " + sunset);
        }
    }
}
