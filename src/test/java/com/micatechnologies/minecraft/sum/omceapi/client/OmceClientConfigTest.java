package com.micatechnologies.minecraft.sum.omceapi.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Transport validation is a security control, not a convenience: it is what stops a bearer token
 * being sent over plaintext by a typo in the config.
 */
class OmceClientConfigTest {

    @Test
    @DisplayName("a well-formed https config validates")
    void acceptsHttps() {
        assertNull(OmceClientConfig.validate("https://economy.example.net", "token", false));
    }

    @Test
    @DisplayName("plaintext http is refused unless explicitly enabled")
    void refusesPlaintextByDefault() {
        String problem = OmceClientConfig.validate("http://economy.example.net", "token", false);
        assertNotNull(problem);
        assertTrue(problem.contains("plaintext"), problem);

        assertNull(OmceClientConfig.validate("http://localhost:8080", "token", true),
            "enableHttp is the documented development escape hatch");
    }

    @Test
    @DisplayName("a missing scheme is refused rather than assumed")
    void refusesSchemelessUrl() {
        assertNotNull(OmceClientConfig.validate("economy.example.net", "token", false));
        assertNotNull(OmceClientConfig.validate("ftp://economy.example.net", "token", false));
    }

    @Test
    @DisplayName("blank url and token are reported separately")
    void refusesBlanks() {
        assertTrue(OmceClientConfig.validate("", "token", false).contains("baseUrl"));
        assertTrue(OmceClientConfig.validate("   ", "token", false).contains("baseUrl"));
        assertTrue(OmceClientConfig.validate("https://x.test", "", false).contains("authToken"));
        assertTrue(OmceClientConfig.validate("https://x.test", "   ", false).contains("authToken"));
    }

    @Test
    @DisplayName("trailing slashes are stripped so endpoint paths join cleanly")
    void normalisesBaseUrl() {
        OmceClientConfig config = OmceClientConfig.builder()
            .baseUrl("https://economy.example.net///")
            .authToken("token")
            .build();
        assertEquals("https://economy.example.net", config.getBaseUrl());
        assertEquals("https://economy.example.net/api/economic/v1/health", config.endpoint("/health"));
    }

    @Test
    @DisplayName("build refuses an invalid configuration")
    void buildValidates() {
        assertThrows(IllegalStateException.class, () -> OmceClientConfig.builder()
            .baseUrl("http://economy.example.net")
            .authToken("token")
            .build());
    }

    @Test
    @DisplayName("a blank instance id falls back to 'default' rather than an empty header")
    void instanceIdDefaults() {
        OmceClientConfig config = OmceClientConfig.builder()
            .baseUrl("https://x.test").authToken("t").instanceId("  ").build();
        assertEquals("default", config.getInstanceId());
    }

    @Test
    @DisplayName("HMAC is off unless a secret is present")
    void hmacOptional() {
        OmceClientConfig off = OmceClientConfig.builder()
            .baseUrl("https://x.test").authToken("t").build();
        assertFalse(off.isHmacEnabled());

        OmceClientConfig on = OmceClientConfig.builder()
            .baseUrl("https://x.test").authToken("t").hmacSecret("s3cret").build();
        assertTrue(on.isHmacEnabled());
    }

    @Test
    @DisplayName("toString never leaks a secret, not even a prefix")
    void toStringRedactsSecrets() {
        OmceClientConfig config = OmceClientConfig.builder()
            .baseUrl("https://economy.example.net")
            .authToken("sea_live_supersecrettoken")
            .hmacSecret("hmac_supersecret")
            .build();
        String rendered = config.toString();
        assertFalse(rendered.contains("supersecret"), rendered);
        assertFalse(rendered.contains("sea_live"), rendered);
        assertTrue(rendered.contains("<set>"));
        assertTrue(rendered.contains("economy.example.net"), "the URL is not a secret");
    }

    @Test
    @DisplayName("timeouts and retries are clamped to sane bounds")
    void clampsTunables() {
        OmceClientConfig config = OmceClientConfig.builder()
            .baseUrl("https://x.test").authToken("t")
            .timeouts(1, 1)
            .maxRetries(500)
            .build();
        assertEquals(250, config.getConnectTimeoutMs());
        assertEquals(250, config.getReadTimeoutMs());
        assertEquals(10, config.getMaxRetries());
    }
}
