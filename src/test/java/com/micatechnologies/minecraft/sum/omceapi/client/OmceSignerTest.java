package com.micatechnologies.minecraft.sum.omceapi.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Signing must be reproducible on the service side from the documented signing string alone, so
 * these tests pin the exact bytes rather than just "it returns something".
 */
class OmceSignerTest {

    private static final String SECRET = "shared-secret";

    @Test
    @DisplayName("the empty-body hash matches the well-known SHA-256 of the empty string")
    void emptyBodyHash() {
        // Any service implementer will compute this constant for GET requests; if our value ever
        // drifted, every signed GET would fail verification.
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            OmceSigner.sha256Hex(null));
        assertEquals(OmceSigner.sha256Hex(null), OmceSigner.sha256Hex(new byte[0]));
    }

    @Test
    @DisplayName("a known body hashes to the known SHA-256")
    void bodyHash() {
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            OmceSigner.sha256Hex("hello".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("signatures are lowercase hex of the expected length")
    void signatureShape() {
        String signature = new OmceSigner(SECRET)
            .sign("GET", "/api/economic/v1/health", 1785500000000L, null);
        assertEquals(64, signature.length(), "HMAC-SHA256 is 32 bytes = 64 hex chars");
        assertTrue(signature.matches("[0-9a-f]{64}"), signature);
    }

    @Test
    @DisplayName("signing is deterministic for identical inputs")
    void deterministic() {
        OmceSigner signer = new OmceSigner(SECRET);
        String a = signer.sign("POST", "/api/economic/v1/processTransaction", 1L,
            "{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        String b = signer.sign("POST", "/api/economic/v1/processTransaction", 1L,
            "{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        assertEquals(a, b);
    }

    @Test
    @DisplayName("every component of the signing string changes the signature")
    void everyComponentMatters() {
        OmceSigner signer = new OmceSigner(SECRET);
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        String base = signer.sign("POST", "/path", 1000L, body);

        assertNotEquals(base, signer.sign("GET", "/path", 1000L, body), "method must be bound");
        assertNotEquals(base, signer.sign("POST", "/other", 1000L, body), "path must be bound");
        assertNotEquals(base, signer.sign("POST", "/path", 1001L, body), "timestamp must be bound");
        assertNotEquals(base, signer.sign("POST", "/path", 1000L,
            "{\"a\":2}".getBytes(StandardCharsets.UTF_8)), "body must be bound");
        assertNotEquals(base, new OmceSigner("other-secret").sign("POST", "/path", 1000L, body),
            "the secret must be bound");
    }

    @Test
    @DisplayName("the query string is part of the signature")
    void queryIsSigned() {
        OmceSigner signer = new OmceSigner(SECRET);
        assertNotEquals(
            signer.sign("GET", "/api/economic/v1/getBalance?playerUuid=a", 1L, null),
            signer.sign("GET", "/api/economic/v1/getBalance?playerUuid=b", 1L, null));
    }

    @Test
    @DisplayName("an empty secret is refused rather than silently producing a weak signature")
    void refusesEmptySecret() {
        assertThrows(IllegalArgumentException.class, () -> new OmceSigner(""));
        assertThrows(IllegalArgumentException.class, () -> new OmceSigner(null));
    }
}
