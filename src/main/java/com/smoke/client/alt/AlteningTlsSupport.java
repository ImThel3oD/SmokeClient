package com.smoke.client.alt;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

final class AlteningTlsSupport {
    private static final String EXTRA_ROOT_CERT_RESOURCE = "/smoke/certs/isrg-root-x1.pem";
    private static final String INSECURE_FALLBACK_PROPERTY = "smoke.altening.insecure_tls_fallback";

    private static volatile SSLSocketFactory fallbackSocketFactory;
    private static volatile SSLSocketFactory insecureSocketFactory;
    private static volatile boolean fallbackInstalledGlobally;

    private AlteningTlsSupport() {
    }

    static void installFallbackAsGlobalDefault() throws Exception {
        if (fallbackInstalledGlobally) {
            return;
        }
        synchronized (AlteningTlsSupport.class) {
            if (fallbackInstalledGlobally) {
                return;
            }
            HttpsURLConnection.setDefaultSSLSocketFactory(getFallbackSocketFactory());
            fallbackInstalledGlobally = true;
        }
    }

    static boolean allowInsecureFallback() {
        String value = System.getProperty(INSECURE_FALLBACK_PROPERTY);
        if (value == null || value.isBlank()) {
            return true;
        }
        return Boolean.parseBoolean(value.trim());
    }

    static void applyFallback(HttpsURLConnection connection) throws Exception {
        connection.setSSLSocketFactory(getFallbackSocketFactory());
    }

    static void applyInsecureFallback(HttpsURLConnection connection, Set<String> allowedHosts) throws Exception {
        connection.setSSLSocketFactory(getInsecureSocketFactory());
        connection.setHostnameVerifier((host, session) ->
                host != null && allowedHosts.contains(host.toLowerCase(Locale.ROOT)));
    }

    private static SSLSocketFactory getFallbackSocketFactory() throws Exception {
        SSLSocketFactory factory = fallbackSocketFactory;
        if (factory != null) {
            return factory;
        }
        synchronized (AlteningTlsSupport.class) {
            if (fallbackSocketFactory == null) {
                fallbackSocketFactory = buildFallbackSocketFactory();
            }
            return fallbackSocketFactory;
        }
    }

    private static SSLSocketFactory buildFallbackSocketFactory() throws Exception {
        X509TrustManager jvmTrustManager = trustManagerFor(null);
        X509TrustManager windowsRootTrustManager = tryWindowsRootTrustManager();

        KeyStore extraStore = KeyStore.getInstance(KeyStore.getDefaultType());
        extraStore.load(null, null);
        try (InputStream certStream = AlteningTlsSupport.class.getResourceAsStream(EXTRA_ROOT_CERT_RESOURCE)) {
            if (certStream == null) {
                throw new IOException("Missing bundled cert resource: " + EXTRA_ROOT_CERT_RESOURCE);
            }
            X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(certStream);
            extraStore.setCertificateEntry("isrg-root-x1", cert);
        }
        X509TrustManager extraTrustManager = trustManagerFor(extraStore);

        X509TrustManager compositeTrustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                jvmTrustManager.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                try {
                    jvmTrustManager.checkServerTrusted(chain, authType);
                    return;
                } catch (CertificateException ignored) {
                }

                try {
                    extraTrustManager.checkServerTrusted(chain, authType);
                    return;
                } catch (CertificateException ignored) {
                }

                if (windowsRootTrustManager != null) {
                    windowsRootTrustManager.checkServerTrusted(chain, authType);
                    return;
                }

                throw new CertificateException("Server certificate chain not trusted by JVM, bundled, or Windows trust stores");
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                X509Certificate[] jvmIssuers = jvmTrustManager.getAcceptedIssuers();
                X509Certificate[] extraIssuers = extraTrustManager.getAcceptedIssuers();
                X509Certificate[] windowsIssuers = windowsRootTrustManager == null
                        ? new X509Certificate[0]
                        : windowsRootTrustManager.getAcceptedIssuers();

                X509Certificate[] all = new X509Certificate[jvmIssuers.length + extraIssuers.length + windowsIssuers.length];
                int offset = 0;
                System.arraycopy(jvmIssuers, 0, all, offset, jvmIssuers.length);
                offset += jvmIssuers.length;
                System.arraycopy(extraIssuers, 0, all, offset, extraIssuers.length);
                offset += extraIssuers.length;
                System.arraycopy(windowsIssuers, 0, all, offset, windowsIssuers.length);
                return all;
            }
        };

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{compositeTrustManager}, null);
        return context.getSocketFactory();
    }

    private static SSLSocketFactory getInsecureSocketFactory() throws Exception {
        SSLSocketFactory factory = insecureSocketFactory;
        if (factory != null) {
            return factory;
        }
        synchronized (AlteningTlsSupport.class) {
            if (insecureSocketFactory == null) {
                insecureSocketFactory = buildInsecureSocketFactory();
            }
            return insecureSocketFactory;
        }
    }

    private static SSLSocketFactory buildInsecureSocketFactory() throws Exception {
        TrustManager[] trustManagers = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagers, new SecureRandom());
        return context.getSocketFactory();
    }

    private static X509TrustManager trustManagerFor(KeyStore keyStore) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);
        for (TrustManager trustManager : tmf.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager x509TrustManager) {
                return x509TrustManager;
            }
        }
        throw new IOException("No X509TrustManager available");
    }

    private static X509TrustManager tryWindowsRootTrustManager() {
        String osName = Objects.toString(System.getProperty("os.name"), "").toLowerCase(Locale.ROOT);
        if (!osName.contains("win")) {
            return null;
        }
        try {
            KeyStore windowsRoot = KeyStore.getInstance("Windows-ROOT");
            windowsRoot.load(null, null);
            return trustManagerFor(windowsRoot);
        } catch (Exception exception) {
            return null;
        }
    }
}
