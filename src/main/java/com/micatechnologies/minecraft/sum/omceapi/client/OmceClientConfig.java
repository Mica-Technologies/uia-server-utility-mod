package com.micatechnologies.minecraft.sum.omceapi.client;

import com.micatechnologies.minecraft.sum.omceapi.OmceProtocol;
import java.io.File;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * An immutable, validated snapshot of the operator's economy API settings.
 *
 * <p>Deliberately decoupled from SUM's {@code SumConfig}: the client package implements the
 * protocol and nothing else, so it takes plain values and can be exercised in tests without a
 * Forge {@code Configuration} on disk. {@code OmceEconomyService} builds one of these from
 * {@code SumConfig} at server start.
 */
public final class OmceClientConfig {

    private final String baseUrl;
    private final String authToken;
    private final String instanceId;
    private final String hmacSecret;
    @Nullable private final File certificateFile;
    private final String certificatePassword;
    private final boolean httpAllowed;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int maxRetries;
    private final boolean sendIntegrityHeaders;
    private final boolean verboseLogging;
    private final String userAgent;

    private OmceClientConfig(Builder b) {
        this.baseUrl = b.baseUrl;
        this.authToken = b.authToken;
        this.instanceId = b.instanceId;
        this.hmacSecret = b.hmacSecret;
        this.certificateFile = b.certificateFile;
        this.certificatePassword = b.certificatePassword;
        this.httpAllowed = b.httpAllowed;
        this.connectTimeoutMs = b.connectTimeoutMs;
        this.readTimeoutMs = b.readTimeoutMs;
        this.maxRetries = b.maxRetries;
        this.sendIntegrityHeaders = b.sendIntegrityHeaders;
        this.verboseLogging = b.verboseLogging;
        this.userAgent = b.userAgent;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Validates a configuration without building it. Kept static and side-effect-free so both the
     * service layer and {@code SumConfig} can surface the same operator-facing message.
     *
     * @return null if usable, otherwise an explanation of what is wrong.
     */
    @Nullable
    public static String validate(String baseUrl, String authToken, boolean httpAllowed) {
        String url = baseUrl == null ? "" : baseUrl.trim();
        if (url.isEmpty()) {
            return "economy_api.baseUrl is blank.";
        }
        String lower = url.toLowerCase(Locale.ROOT);
        boolean https = lower.startsWith("https://");
        boolean http = lower.startsWith("http://");
        if (!https && !http) {
            return "economy_api.baseUrl must start with https:// (got '" + url + "').";
        }
        if (http && !httpAllowed) {
            return "economy_api.baseUrl uses plaintext http://, which is refused for security. "
                + "Use https://, or for a self-signed certificate set economy_api.certificatePath. "
                + "Set economy_api.enableHttp=true only for local development.";
        }
        if (authToken == null || authToken.trim().isEmpty()) {
            return "economy_api.authToken is blank.";
        }
        return null;
    }

    /** Full URL for an endpoint path, e.g. {@code https://host/api/economic/v1/health}. */
    public String endpoint(String path) {
        return baseUrl + OmceProtocol.BASE_PATH + path;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getAuthToken() {
        return authToken;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getHmacSecret() {
        return hmacSecret;
    }

    public boolean isHmacEnabled() {
        return !hmacSecret.isEmpty();
    }

    /** Extra certificate or CA bundle to trust, or null to use only the system trust store. */
    @Nullable
    public File getCertificateFile() {
        return certificateFile;
    }

    public String getCertificatePassword() {
        return certificatePassword;
    }

    /** True when the operator opted into plaintext HTTP. Always logged loudly. */
    public boolean isPlaintext() {
        return baseUrl.toLowerCase(Locale.ROOT).startsWith("http://");
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public boolean isSendIntegrityHeaders() {
        return sendIntegrityHeaders;
    }

    public boolean isVerboseLogging() {
        return verboseLogging;
    }

    public String getUserAgent() {
        return userAgent;
    }

    /**
     * A form of this config safe to write to a log: the token, HMAC secret, and certificate
     * password are replaced with a fixed marker rather than truncated, so no prefix of a secret
     * ever reaches disk.
     */
    @Override
    public String toString() {
        return "OmceClientConfig{baseUrl=" + baseUrl
            + ", instance=" + instanceId
            + ", token=" + redact(authToken)
            + ", hmac=" + (isHmacEnabled() ? "<set>" : "<disabled>")
            + ", certificate=" + (certificateFile == null ? "<system trust>" : certificateFile.getPath())
            + ", connectTimeoutMs=" + connectTimeoutMs
            + ", readTimeoutMs=" + readTimeoutMs
            + ", maxRetries=" + maxRetries
            + ", integrityHeaders=" + sendIntegrityHeaders
            + "}";
    }

    private static String redact(String secret) {
        return (secret == null || secret.isEmpty()) ? "<unset>" : "<set>";
    }

    /** Builder; call {@link #build()} only after {@link #validate} has passed. */
    public static final class Builder {

        private String baseUrl = "";
        private String authToken = "";
        private String instanceId = "default";
        private String hmacSecret = "";
        @Nullable private File certificateFile;
        private String certificatePassword = "";
        private boolean httpAllowed;
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;
        private int maxRetries = 2;
        private boolean sendIntegrityHeaders = true;
        private boolean verboseLogging;
        private String userAgent = "SUM/unknown OpenMCEconomicAPI/" + OmceProtocol.VERSION;

        public Builder baseUrl(String value) {
            String trimmed = value == null ? "" : value.trim();
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            this.baseUrl = trimmed;
            return this;
        }

        public Builder authToken(String value) {
            this.authToken = value == null ? "" : value.trim();
            return this;
        }

        public Builder instanceId(String value) {
            String trimmed = value == null ? "" : value.trim();
            this.instanceId = trimmed.isEmpty() ? "default" : trimmed;
            return this;
        }

        public Builder hmacSecret(String value) {
            this.hmacSecret = value == null ? "" : value.trim();
            return this;
        }

        public Builder certificate(@Nullable File file, String password) {
            this.certificateFile = file;
            this.certificatePassword = password == null ? "" : password;
            return this;
        }

        public Builder httpAllowed(boolean value) {
            this.httpAllowed = value;
            return this;
        }

        public Builder timeouts(int connectMs, int readMs) {
            this.connectTimeoutMs = Math.max(250, connectMs);
            this.readTimeoutMs = Math.max(250, readMs);
            return this;
        }

        public Builder maxRetries(int value) {
            this.maxRetries = Math.max(0, Math.min(10, value));
            return this;
        }

        public Builder sendIntegrityHeaders(boolean value) {
            this.sendIntegrityHeaders = value;
            return this;
        }

        public Builder verboseLogging(boolean value) {
            this.verboseLogging = value;
            return this;
        }

        public Builder userAgent(String value) {
            if (value != null && !value.isEmpty()) {
                this.userAgent = value;
            }
            return this;
        }

        public OmceClientConfig build() {
            String problem = validate(baseUrl, authToken, httpAllowed);
            if (problem != null) {
                throw new IllegalStateException(problem);
            }
            return new OmceClientConfig(this);
        }
    }
}
