package com.smoke.client.alt;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.smoke.client.mixin.accessor.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AlteningService {
    private static final String ALTENING_AUTH_BASE = "http://authserver.thealtening.com/";
    private static final Gson GSON = new Gson();

    private static volatile boolean serviceRedirected = false;
    private static volatile boolean altSessionActive = false;
    private static volatile Session originalSession = null;

    private AlteningService() {
    }

    public static void switchToTheAltening() {
        serviceRedirected = true;
        try {
            AlteningApiClient.installTlsFallbackAsGlobalDefault();
        } catch (Throwable t) {
            System.err.println("[Smoke] Failed to install Altening TLS fallback: " + t.getMessage());
        }
    }

    public static void switchToMojang() {
        serviceRedirected = false;
    }

    public static boolean isServiceRedirected() {
        return serviceRedirected;
    }

    public static boolean isAltSessionActive() {
        return altSessionActive;
    }

    public static boolean hasOriginalSession() {
        return originalSession != null;
    }


    public static AuthResult authenticate(String token) throws Exception {
        JsonObject payload = new JsonObject();
        JsonObject agent = new JsonObject();
        agent.addProperty("name", "Minecraft");
        agent.addProperty("version", 1);
        payload.add("agent", agent);
        payload.addProperty("username", token);
        payload.addProperty("password", "SmokeClient");

        HttpURLConnection conn = (HttpURLConnection) URI.create(ALTENING_AUTH_BASE + "authenticate")
                .toURL()
                .openConnection();

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(false);

            byte[] body = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }

            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                throw new Exception("Unexpected redirect from authentication endpoint");
            }
            if (code != 200) {
                String error = "Authentication failed (HTTP " + code + ")";
                InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    try (InputStreamReader reader = new InputStreamReader(errorStream, StandardCharsets.UTF_8)) {
                        JsonObject errJson = GSON.fromJson(reader, JsonObject.class);
                        if (errJson != null && errJson.has("errorMessage")) {
                            error = errJson.get("errorMessage").getAsString();
                        }
                    }
                }
                throw new Exception(error);
            }

            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject response = GSON.fromJson(reader, JsonObject.class);
                String accessToken = response.get("accessToken").getAsString();
                JsonObject profile = response.getAsJsonObject("selectedProfile");
                String uuid = profile.get("id").getAsString();
                String username = profile.get("name").getAsString();
                return new AuthResult(accessToken, uuid, username);
            }
        } finally {
            conn.disconnect();
        }
    }


    public static String login(AltAccount alt) throws Exception {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (originalSession == null) {
            originalSession = mc.getSession();
        }

        switchToTheAltening();
        AuthResult result = authenticate(alt.getToken());
        UUID uuid = parseUUID(result.uuid);

        Session newSession = new Session(
                result.username,
                uuid,
                result.accessToken,
                Optional.empty(),
                Optional.empty(),
                Session.AccountType.MOJANG
        );

        ((MinecraftClientAccessor) (Object) mc).smoke$setSession(newSession);
        altSessionActive = true;
        alt.setSession(result.accessToken, result.uuid, result.username);
        return result.username;
    }

    public static void restoreOriginalSession() {
        if (originalSession == null) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ((MinecraftClientAccessor) (Object) mc).smoke$setSession(originalSession);
        switchToMojang();
        altSessionActive = false;
        originalSession = null;
    }

    public static AltData loadData() {
        File file = getDataFile();
        if (!file.exists()) {
            return new AltData();
        }

        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            AltData data = GSON.fromJson(reader, AltData.class);
            return data != null ? data : new AltData();
        } catch (Exception e) {
            System.err.println("[Smoke] Failed to load alt data: " + e.getMessage());
            return new AltData();
        }
    }

    public static void saveData(AltData data) {
        File file = getDataFile();
        File parentFile = file.getParentFile();
        if (parentFile != null) {
            parentFile.mkdirs();
        }

        try (Writer writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        } catch (Exception e) {
            System.err.println("[Smoke] Failed to save alt data: " + e.getMessage());
        }
    }

    private static File getDataFile() {
        return new File(MinecraftClient.getInstance().runDirectory, "smoke/alts.json");
    }

    private static UUID parseUUID(String uuid) {
        if (uuid.contains("-")) {
            return UUID.fromString(uuid);
        }
        return UUID.fromString(
                uuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5")
        );
    }

    public record AuthResult(String accessToken, String uuid, String username) {
    }

    public static final class AltData {
        public String apiKey = "";
        public List<AltAccount> alts = new ArrayList<>();
    }
}
