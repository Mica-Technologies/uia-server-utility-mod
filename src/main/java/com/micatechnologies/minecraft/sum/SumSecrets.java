package com.micatechnologies.minecraft.sum;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import javax.annotation.Nullable;
import net.minecraftforge.common.config.Configuration;

/**
 * Reads the economy API's secrets ({@code authToken}, {@code hmacSecret},
 * {@code certificatePassword}) from places a modpack never manages, so {@code sum.cfg} itself can
 * be shipped by pack tooling without carrying a secret.
 *
 * <p>Each secret is taken from the first of these with a non-blank value:
 * <ol>
 *   <li>an environment variable ({@code SUM_ECONOMY_AUTH_TOKEN}, {@code SUM_ECONOMY_HMAC_SECRET},
 *       {@code SUM_ECONOMY_CERT_PASSWORD});</li>
 *   <li>the secrets file, {@code economy_api.secretsFile} in {@code sum.cfg} (default
 *       {@code sum-secrets.cfg}, next to {@code sum.cfg});</li>
 *   <li>{@code sum.cfg} itself, so servers configured before the secrets file existed keep
 *       working.</li>
 * </ol>
 *
 * <p>The secrets file is read wherever it exists, but only ever <em>created</em> on a dedicated
 * server with the economy API enabled, so a client install never grows an empty secrets file.
 */
final class SumSecrets {

    static final String DEFAULT_FILE_NAME = "sum-secrets.cfg";

    static final String CATEGORY = "economy_api";

    static final String KEY_AUTH_TOKEN = "authToken";
    static final String KEY_HMAC_SECRET = "hmacSecret";
    static final String KEY_CERT_PASSWORD = "certificatePassword";

    static final String ENV_AUTH_TOKEN = "SUM_ECONOMY_AUTH_TOKEN";
    static final String ENV_HMAC_SECRET = "SUM_ECONOMY_HMAC_SECRET";
    static final String ENV_CERT_PASSWORD = "SUM_ECONOMY_CERT_PASSWORD";

    private static final String CATEGORY_COMMENT =
        "Secrets for the Open MCEconomic API client. Keep this file on the server only: never put "
            + "it in a modpack or anywhere it is shared. A non-blank value here overrides the same "
            + "key in sum.cfg, and an environment variable overrides both. Everything else about "
            + "the economy API (enabled, baseUrl, instanceId, ...) stays in sum.cfg.";

    private static final String DESCRIPTION_AUTH_TOKEN =
        "Bearer token issued by the economy service operator. Anyone holding it can move money. "
            + "Overridden by the " + ENV_AUTH_TOKEN + " environment variable.";
    private static final String DESCRIPTION_HMAC_SECRET =
        "Optional HMAC-SHA256 request signing secret. Leave blank unless the service verifies "
            + "signatures. Overridden by the " + ENV_HMAC_SECRET + " environment variable.";
    private static final String DESCRIPTION_CERT_PASSWORD =
        "Password for economy_api.certificatePath (in sum.cfg) when it is a PKCS#12 or JKS "
            + "keystore. Overridden by the " + ENV_CERT_PASSWORD + " environment variable.";

    /** A resolved secret and a human-readable name for where it came from. Never log {@link #value}. */
    static final class Resolved {
        final String value;
        final String source;

        Resolved(String value, String source) {
            this.value = value;
            this.source = source;
        }
    }

    private SumSecrets() {
    }

    /**
     * Picks the first non-blank of the environment, secrets-file and {@code sum.cfg} values. A
     * blank result reports source {@code "unset"} and the value from {@code sum.cfg} (blank or
     * whitespace), so behaviour with nothing configured is unchanged.
     */
    static Resolved resolve(@Nullable String envValue, String envName, @Nullable String fileValue,
        String fileName, String configValue) {
        if (!isBlank(envValue)) {
            return new Resolved(envValue, "environment variable " + envName);
        }
        if (!isBlank(fileValue)) {
            return new Resolved(fileValue, fileName);
        }
        if (!isBlank(configValue)) {
            return new Resolved(configValue, "sum.cfg");
        }
        return new Resolved(configValue, "unset");
    }

    /**
     * Resolves the secrets file name from {@code sum.cfg} against the config directory. An
     * absolute path is used as is; a blank one falls back to {@link #DEFAULT_FILE_NAME}.
     */
    static File resolveFile(File configDirectory, String configured) {
        String name = isBlank(configured) ? DEFAULT_FILE_NAME : configured.trim();
        File candidate = new File(name);
        return candidate.isAbsolute() ? candidate : new File(configDirectory, name);
    }

    /**
     * Loads the secrets file, creating it from a commented template first when
     * {@code createIfMissing} is set. Returns null when the file does not exist and was not
     * created, so the caller falls back to {@code sum.cfg}.
     */
    @Nullable
    static Configuration load(File file, boolean createIfMissing) {
        boolean existed = file.isFile();
        if (!existed && !createIfMissing) {
            // Configuration.load() would create an empty file; do not construct one at all.
            return null;
        }
        Configuration secrets = new Configuration(file);
        secrets.addCustomCategoryComment(CATEGORY, CATEGORY_COMMENT);
        secrets.getString(KEY_AUTH_TOKEN, CATEGORY, "", DESCRIPTION_AUTH_TOKEN);
        secrets.getString(KEY_HMAC_SECRET, CATEGORY, "", DESCRIPTION_HMAC_SECRET);
        secrets.getString(KEY_CERT_PASSWORD, CATEGORY, "", DESCRIPTION_CERT_PASSWORD);
        if (createIfMissing && secrets.hasChanged()) {
            secrets.save();
            if (!existed) {
                restrictToOwner(file);
                Sum.LOGGER.info("[omce] Created {} for the economy API secrets. Put authToken "
                    + "there rather than in sum.cfg if a modpack manages sum.cfg.", file.getName());
            }
        }
        return secrets;
    }

    static String get(@Nullable Configuration secrets, String key) {
        if (secrets == null) {
            return null;
        }
        return secrets.getCategory(CATEGORY).containsKey(key)
            ? secrets.getCategory(CATEGORY).get(key).getString()
            : null;
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Best effort: owner read/write only where the filesystem supports POSIX permissions. */
    private static void restrictToOwner(File file) {
        try {
            Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException | SecurityException ignored) {
            // Windows and other non-POSIX filesystems: leave the default permissions.
        }
    }
}
