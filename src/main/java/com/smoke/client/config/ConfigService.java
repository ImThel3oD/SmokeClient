package com.smoke.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smoke.client.SmokeClient;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleManager;
import com.smoke.client.setting.KeyBindSetting;
import com.smoke.client.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;

public final class ConfigService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path baseDir;
    private final Path modulesFile;
    private final Path settingsFile;
    private final Path uiFile;
    private final JsonObject uiState = new JsonObject();

    public ConfigService(Path configRoot) {
        Path root = configRoot == null ? FabricLoader.getInstance().getConfigDir() : configRoot;
        this.baseDir = root.resolve("smoke");
        this.modulesFile = baseDir.resolve("modules.json");
        this.settingsFile = baseDir.resolve("settings.json");
        this.uiFile = baseDir.resolve("ui.json");
    }

    public synchronized void load(ModuleManager moduleManager) {
        Objects.requireNonNull(moduleManager, "moduleManager");
        JsonObject moduleStateRoot = readObject(modulesFile);
        JsonObject settingRoot = readObject(settingsFile);
        JsonObject uiRoot = readObject(uiFile);

        clearObject(uiState);
        for (String key : uiRoot.keySet()) {
            uiState.add(key, uiRoot.get(key).deepCopy());
        }

        for (Module module : moduleManager.all()) {
            JsonObject moduleState = getObject(moduleStateRoot, module.id());
            if (moduleState != null) {
                if (moduleState.has("enabled")) {
                    moduleManager.setEnabled(module, moduleState.get("enabled").getAsBoolean());
                }
                if (moduleState.has("keybind")) {
                    module.keybind().setValue(moduleState.get("keybind").getAsInt());
                }
            }

            JsonObject moduleSettings = getObject(settingRoot, module.id());
            if (moduleSettings == null) {
                continue;
            }

            for (Setting<?> setting : module.settings()) {
                if (setting instanceof KeyBindSetting) {
                    continue;
                }
                if (moduleSettings.has(setting.id())) {
                    setting.fromJson(moduleSettings.get(setting.id()));
                }
            }
        }
    }

    public synchronized void save(ModuleManager moduleManager) {
        Objects.requireNonNull(moduleManager, "moduleManager");
        JsonObject moduleStateRoot = new JsonObject();
        JsonObject settingRoot = new JsonObject();

        for (Module module : moduleManager.all()) {
            JsonObject moduleState = new JsonObject();
            moduleState.addProperty("enabled", module.enabled());
            moduleState.addProperty("keybind", module.keybind().value());
            moduleStateRoot.add(module.id(), moduleState);

            JsonObject moduleSettings = new JsonObject();
            for (Setting<?> setting : module.settings()) {
                if (setting instanceof KeyBindSetting) {
                    continue;
                }
                moduleSettings.add(setting.id(), setting.toJson());
            }
            settingRoot.add(module.id(), moduleSettings);
        }

        writeObject(modulesFile, moduleStateRoot);
        writeObject(settingsFile, settingRoot);
        writeObject(uiFile, uiState.deepCopy());
    }

    public synchronized void putUiValue(String section, String key, JsonElement value) {
        JsonObject sectionObject = uiState.has(section) && uiState.get(section).isJsonObject()
                ? uiState.getAsJsonObject(section)
                : new JsonObject();
        sectionObject.add(key, value);
        uiState.add(section, sectionObject);
    }

    public synchronized Optional<JsonElement> getUiValue(String section, String key) {
        if (!uiState.has(section) || !uiState.get(section).isJsonObject()) {
            return Optional.empty();
        }
        JsonObject sectionObject = uiState.getAsJsonObject(section);
        if (!sectionObject.has(key)) {
            return Optional.empty();
        }
        return Optional.of(sectionObject.get(key).deepCopy());
    }

    private JsonObject readObject(Path file) {
        if (Files.notExists(file)) {
            return new JsonObject();
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (Exception exception) {
            SmokeClient.LOGGER.warn("Failed to read config file {}: {}", file, exception.getMessage());
            return new JsonObject();
        }
    }

    private void writeObject(Path file, JsonObject root) {
        try {
            Files.createDirectories(baseDir);
            Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(
                    tempFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            )) {
                GSON.toJson(root, writer);
            }

            try {
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new RuntimeException("Failed to save config " + file, exception);
        }
    }

    private static JsonObject getObject(JsonObject root, String key) {
        if (root == null || !root.has(key) || !root.get(key).isJsonObject()) {
            return null;
        }
        return root.getAsJsonObject(key);
    }

    private static void clearObject(JsonObject object) {
        object.entrySet().removeIf(entry -> true);
    }
}
