package com.micatechnologies.minecraft.sum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the precedence and file-resolution rules for the economy secrets in {@link SumSecrets}:
 * environment variable, then secrets file, then sum.cfg, with blanks falling through.
 */
class SumSecretsTest {

    private static SumSecrets.Resolved resolve(String env, String file, String cfg) {
        return SumSecrets.resolve(env, "SUM_ECONOMY_AUTH_TOKEN", file, "sum-secrets.cfg", cfg);
    }

    @Test
    void environmentBeatsFileAndConfig() {
        SumSecrets.Resolved r = resolve("env-token", "file-token", "cfg-token");
        assertEquals("env-token", r.value);
        assertEquals("environment variable SUM_ECONOMY_AUTH_TOKEN", r.source);
    }

    @Test
    void secretsFileBeatsConfig() {
        SumSecrets.Resolved r = resolve(null, "file-token", "cfg-token");
        assertEquals("file-token", r.value);
        assertEquals("sum-secrets.cfg", r.source);
    }

    @Test
    void configIsTheFallback() {
        SumSecrets.Resolved r = resolve(null, null, "cfg-token");
        assertEquals("cfg-token", r.value);
        assertEquals("sum.cfg", r.source);
    }

    @Test
    void blankValuesFallThrough() {
        SumSecrets.Resolved r = resolve("   ", "", "cfg-token");
        assertEquals("cfg-token", r.value);
        assertEquals("sum.cfg", r.source);
    }

    @Test
    void nothingSetIsUnset() {
        SumSecrets.Resolved r = resolve("", " ", "");
        assertEquals("", r.value);
        assertEquals("unset", r.source);
    }

    @Test
    void relativeFileResolvesAgainstConfigDirectory(@TempDir Path dir) {
        File configDir = dir.toFile();
        assertEquals(new File(configDir, "sum-secrets.cfg"), SumSecrets.resolveFile(configDir, ""));
        assertEquals(new File(configDir, "private/economy.cfg"),
            SumSecrets.resolveFile(configDir, " private/economy.cfg "));
    }

    @Test
    void absoluteFileIsUsedAsIs(@TempDir Path dir) {
        File absolute = dir.resolve("elsewhere.cfg").toFile().getAbsoluteFile();
        assertEquals(absolute, SumSecrets.resolveFile(new File("config"), absolute.getPath()));
    }

    @Test
    void missingFileIsNotCreatedUnlessAsked(@TempDir Path dir) {
        File secrets = dir.resolve("sum-secrets.cfg").toFile();
        assertNull(SumSecrets.load(secrets, false));
        assertFalse(secrets.exists(), "a client must never get an empty secrets file");
    }

    @Test
    void getFromMissingFileIsNull() {
        assertNull(SumSecrets.get(null, SumSecrets.KEY_AUTH_TOKEN));
    }
}
