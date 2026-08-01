package com.micatechnologies.minecraft.sum.omceapi.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 request signing (spec section 5.2), an optional second layer on top of the bearer
 * token. The token says who is calling; the signature proves the request was not tampered with or
 * replayed.
 *
 * <p>The signing string is:
 *
 * <pre>
 *   METHOD \n PATH_WITH_QUERY \n TIMESTAMP \n SHA256_HEX(rawBody)
 * </pre>
 *
 * <p>The body hash is taken over the exact bytes sent, never a re-serialisation, so the two sides
 * cannot disagree over JSON key ordering or whitespace.
 */
public final class OmceSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** SHA-256 of the empty string, used for GET requests, precomputed to avoid rehashing it. */
    private static final String EMPTY_BODY_SHA256 =
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private final byte[] secret;

    public OmceSigner(String sharedSecret) {
        if (sharedSecret == null || sharedSecret.isEmpty()) {
            throw new IllegalArgumentException("HMAC signing requires a non-empty shared secret.");
        }
        this.secret = sharedSecret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Builds the signature for one request.
     *
     * @param method HTTP method, uppercase.
     * @param pathWithQuery request path including any query string, e.g. {@code /api/economic/v1/getBalance?playerUuid=...}
     * @param timestampMillis value sent in the {@code X-MCE-Timestamp} header.
     * @param rawBody exact request body bytes, or null for a request without one.
     * @return lowercase hex HMAC-SHA256.
     */
    public String sign(String method, String pathWithQuery, long timestampMillis, byte[] rawBody) {
        String signingString = method + "\n"
            + pathWithQuery + "\n"
            + timestampMillis + "\n"
            + sha256Hex(rawBody);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return toHex(mac.doFinal(signingString.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // HmacSHA256 is mandatory on every Java platform; if it's genuinely missing, signing
            // cannot be done at all and silently sending an unsigned request would be worse.
            throw new IllegalStateException("HMAC-SHA256 is unavailable on this JVM.", e);
        }
    }

    /** SHA-256 of the body as lowercase hex, matching the spec's {@code SHA256_HEX}. */
    public static String sha256Hex(byte[] body) {
        if (body == null || body.length == 0) {
            return EMPTY_BODY_SHA256;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable on this JVM.", e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
