package com.smoke.client.alt;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.smoke.client.util.PrivacySanitizer;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public final class AlteningApiClient {
    private static final String BASE_URL_HTTPS = "https://api.thealtening.com/v2/";
    private static final String ALTENING_API_HOST = "api.thealtening.com";
    private static final Gson GSON = new Gson();
    private static final int MAX_REDIRECTS = 3;

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "api.thealtening.com",
            "authserver.thealtening.com",
            "sessionserver.thealtening.com"
    );

    private AlteningApiClient() {
    }

    public static AltAccount generate(String apiKey) throws Exception {
        String path = "generate?info=true&key=" + apiKey;
        String responseBody;
        try {
            responseBody = httpGet(BASE_URL_HTTPS + path);
        } catch (Exception e) {
            if (isPkixPathFailure(e)) {
                throw new Exception("Certificate trust failed (PKIX). Check clock and disable HTTPS scanning in proxy/VPN/AV.", e);
            }
            throw new Exception("Failed to reach TheAltening API over HTTPS: " + safeMessage(e), e);
        }

        JsonObject json = GSON.fromJson(responseBody, JsonObject.class);
        if (json == null) {
            throw new Exception("Empty response from API");
        }

        String token = json.get("token").getAsString();
        String username = json.has("username") ? json.get("username").getAsString() : "Unknown";
        boolean limit = json.has("limit") && json.get("limit").getAsBoolean();
        if (limit) {
            throw new Exception("Daily generation limit reached");
        }

        return new AltAccount(token, username);
    }

    public static void installTlsFallbackAsGlobalDefault() throws Exception {
        AlteningTlsSupport.installFallbackAsGlobalDefault();
    }

    public static boolean isPkixPathFailure(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("pkix path building failed")
                        || normalized.contains("unable to find valid certification path")
                        || normalized.contains("suncertpathbuilderexception")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static String httpGet(String url) throws Exception {
        String currentUrl = url;
        if (!isHttpsUrl(currentUrl) || !isAllowedApiHost(currentUrl)) {
            throw new Exception("Blocked non-approved Altening API URL");
        }

        int redirects = 0;
        boolean pkixFallback = false;
        boolean insecureFallback = false;

        while (redirects < MAX_REDIRECTS) {
            HttpURLConnection conn = insecureFallback
                    ? openInsecureAllowedHostConnection(currentUrl)
                    : openHttpConnection(currentUrl, pkixFallback);

            try {
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("User-Agent", "SmokeClient/1.0");
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = conn.getHeaderField("Location");
                    if (location == null || location.isEmpty()) {
                        throw new Exception("Redirect with no Location header");
                    }
                    String redirectedUrl = URI.create(currentUrl).resolve(location).toString();
                    if (!isHttpsUrl(redirectedUrl) || !isAllowedApiHost(redirectedUrl)) {
                        throw new Exception("Blocked redirect to non-approved Altening API host");
                    }
                    currentUrl = redirectedUrl;
                    redirects++;
                    continue;
                }

                if (code == 403) {
                    throw new Exception("Invalid API key or no API access on your plan");
                }
                if (code == 429) {
                    throw new Exception("Rate limited, try again later");
                }
                if (code != 200) {
                    throw new Exception("TheAltening API returned HTTP " + code);
                }

                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    StringBuilder sb = new StringBuilder();
                    char[] buf = new char[4096];
                    int n;
                    while ((n = reader.read(buf)) != -1) {
                        sb.append(buf, 0, n);
                    }
                    return sb.toString();
                }
            } catch (SSLHandshakeException sslHandshakeException) {
                if (!pkixFallback && isPkixPathFailure(sslHandshakeException)) {
                    pkixFallback = true;
                    AlteningTlsSupport.installFallbackAsGlobalDefault();
                    continue;
                }
                if (isPkixPathFailure(sslHandshakeException)
                        && !insecureFallback
                        && AlteningTlsSupport.allowInsecureFallback()) {
                    insecureFallback = true;
                    continue;
                }
                throw sslHandshakeException;
            } finally {
                conn.disconnect();
            }
        }

        throw new Exception("Too many redirects");
    }

    private static boolean isHttpsUrl(String url) {
        return "https".equalsIgnoreCase(URI.create(url).getScheme());
    }

    private static boolean isAllowedApiHost(String url) {
        String host = URI.create(url).getHost();
        return ALTENING_API_HOST.equalsIgnoreCase(host);
    }

    private static HttpURLConnection openHttpConnection(String url, boolean usePkixFallback) throws Exception {
        URI uri = URI.create(url);
        if (!isAllowed(uri)) {
            throw new IOException("Blocked non-approved Altening HTTPS URL");
        }

        URLConnection raw = uri.toURL().openConnection();
        if (!(raw instanceof HttpURLConnection connection)) {
            throw new IOException("Unsupported connection type");
        }

        if (usePkixFallback) {
            if (!(connection instanceof HttpsURLConnection httpsConnection)) {
                throw new IOException("PKIX fallback requires HTTPS connection");
            }
            AlteningTlsSupport.applyFallback(httpsConnection);
        }

        return connection;
    }

    private static HttpURLConnection openInsecureAllowedHostConnection(String url) throws Exception {
        URI uri = URI.create(url);
        if (!isAllowed(uri)) {
            throw new IOException("Blocked non-approved Altening HTTPS URL");
        }

        URLConnection raw = uri.toURL().openConnection();
        if (!(raw instanceof HttpsURLConnection httpsConnection)) {
            throw new IOException("Insecure fallback requires HTTPS connection");
        }

        AlteningTlsSupport.applyInsecureFallback(httpsConnection, ALLOWED_HOSTS);
        return httpsConnection;
    }

    private static boolean isAllowed(URI uri) {
        if (uri == null) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        return ALLOWED_HOSTS.contains(host.toLowerCase(Locale.ROOT));
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "unknown error";
        }
        return PrivacySanitizer.sanitize(throwable.getMessage().trim());
    }
}
