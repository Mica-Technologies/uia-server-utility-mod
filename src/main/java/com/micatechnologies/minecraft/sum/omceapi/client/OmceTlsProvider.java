package com.micatechnologies.minecraft.sum.omceapi.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Locale;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Builds the {@link SSLSocketFactory} used for economy requests.
 *
 * <p>With no operator certificate configured this returns null and the JDK default (system trust
 * store, hostname verification on) is used unchanged. With one configured, it builds a trust
 * manager that trusts the system anchors <b>plus</b> the supplied certificate, so a private CA can
 * be added without losing the ability to verify public ones.
 *
 * <p>There is deliberately no "disable verification" path. A self-signed deployment supplies its
 * certificate here; an operator who cannot is better served by a loud failure than by a connection
 * that silently accepts any certificate an attacker presents.
 */
public final class OmceTlsProvider {

    private OmceTlsProvider() {}

    /**
     * @param certificateFile PEM, PKCS#12, or JKS file to trust in addition to the system anchors;
     *     null to use system trust only.
     * @param password required for PKCS#12 and JKS, ignored for PEM.
     * @return a factory to install on the connection, or null to use the JDK default.
     * @throws OmceTlsException if the certificate is configured but unusable. Callers must treat
     *     this as fatal rather than falling back to an unverified connection.
     */
    @Nullable
    public static SSLSocketFactory createSocketFactory(@Nullable File certificateFile, String password)
        throws OmceTlsException {
        if (certificateFile == null) {
            return null;
        }
        if (!certificateFile.isFile()) {
            throw new OmceTlsException("economy_api.certificatePath does not point at a file: "
                + certificateFile.getAbsolutePath());
        }
        if (!certificateFile.canRead()) {
            throw new OmceTlsException("economy_api.certificatePath is not readable: "
                + certificateFile.getAbsolutePath());
        }
        try {
            KeyStore trustStore = isKeystore(certificateFile)
                ? loadKeystore(certificateFile, password)
                : loadPem(certificateFile);
            return buildFactory(trustStore);
        } catch (OmceTlsException e) {
            throw e;
        } catch (Exception e) {
            throw new OmceTlsException("Could not load economy_api.certificatePath ("
                + certificateFile.getAbsolutePath() + "): " + e.getMessage(), e);
        }
    }

    private static boolean isKeystore(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".p12") || name.endsWith(".pfx") || name.endsWith(".jks");
    }

    private static KeyStore loadKeystore(File file, String password) throws Exception {
        String name = file.getName().toLowerCase(Locale.ROOT);
        String type = name.endsWith(".jks") ? "JKS" : "PKCS12";
        if (password == null || password.isEmpty()) {
            throw new OmceTlsException("economy_api.certificatePath is a " + type
                + " keystore, which requires economy_api.certificatePassword.");
        }
        KeyStore store = KeyStore.getInstance(type);
        try (InputStream in = new FileInputStream(file)) {
            store.load(in, password.toCharArray());
        }
        return store;
    }

    private static KeyStore loadPem(File file) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certificates;
        try (InputStream in = new FileInputStream(file)) {
            // generateCertificates reads every BEGIN CERTIFICATE block, so a full CA bundle works.
            certificates = factory.generateCertificates(in);
        }
        if (certificates.isEmpty()) {
            throw new OmceTlsException("No certificates found in " + file.getAbsolutePath()
                + ". Expected a PEM file containing at least one BEGIN CERTIFICATE block.");
        }
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        int index = 0;
        for (Certificate certificate : certificates) {
            if (certificate instanceof X509Certificate) {
                // Surface an expired certificate now, with a clear message, rather than as an
                // opaque handshake failure on the first economy request.
                try {
                    ((X509Certificate) certificate).checkValidity();
                } catch (Exception e) {
                    throw new OmceTlsException("Certificate in " + file.getName()
                        + " is expired or not yet valid: " + e.getMessage(), e);
                }
            }
            store.setCertificateEntry("omce-" + index, certificate);
            index++;
        }
        return store;
    }

    /**
     * Builds a factory whose trust manager consults the supplied store first, then the platform
     * default. Chaining rather than replacing is what lets a private CA coexist with public ones.
     */
    private static SSLSocketFactory buildFactory(KeyStore extraTrust) throws Exception {
        X509TrustManager custom = firstX509(trustManagers(extraTrust));
        X509TrustManager system = firstX509(trustManagers(null));
        if (custom == null) {
            throw new OmceTlsException("The supplied certificate produced no usable trust manager.");
        }
        TrustManager combined = new CombinedTrustManager(custom, system);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] { combined }, null);
        return context.getSocketFactory();
    }

    private static TrustManager[] trustManagers(@Nullable KeyStore store) throws Exception {
        TrustManagerFactory factory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        return factory.getTrustManagers();
    }

    @Nullable
    private static X509TrustManager firstX509(TrustManager[] managers) {
        for (TrustManager manager : managers) {
            if (manager instanceof X509TrustManager) {
                return (X509TrustManager) manager;
            }
        }
        return null;
    }

    /**
     * Accepts a chain if either the operator's certificate or the platform trust store accepts it.
     * Hostname verification is unaffected and still applied by {@code HttpsURLConnection}.
     */
    private static final class CombinedTrustManager implements X509TrustManager {

        private final X509TrustManager custom;
        @Nullable private final X509TrustManager system;

        CombinedTrustManager(X509TrustManager custom, @Nullable X509TrustManager system) {
            this.custom = custom;
            this.system = system;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws java.security.cert.CertificateException {
            custom.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws java.security.cert.CertificateException {
            try {
                custom.checkServerTrusted(chain, authType);
            } catch (java.security.cert.CertificateException primary) {
                if (system == null) {
                    throw primary;
                }
                try {
                    system.checkServerTrusted(chain, authType);
                } catch (java.security.cert.CertificateException secondary) {
                    // Report the operator's own certificate as the cause — that's the one they
                    // configured and the one they can act on.
                    throw primary;
                }
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] mine = custom.getAcceptedIssuers();
            if (system == null) {
                return mine;
            }
            X509Certificate[] theirs = system.getAcceptedIssuers();
            X509Certificate[] all = new X509Certificate[mine.length + theirs.length];
            System.arraycopy(mine, 0, all, 0, mine.length);
            System.arraycopy(theirs, 0, all, mine.length, theirs.length);
            return all;
        }
    }

    /** Thrown when a configured certificate cannot be used. Always fatal to the economy backend. */
    public static final class OmceTlsException extends IOException {

        private static final long serialVersionUID = 1L;

        public OmceTlsException(String message) {
            super(message);
        }

        public OmceTlsException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
