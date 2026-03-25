package com.smoke.client.module;

import com.smoke.client.setting.KeyBindSetting;
import com.smoke.client.setting.NumberSetting;
import com.smoke.client.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class Module {
    private final ModuleContext context;
    private final String id;
    private final String name;
    private final String description;
    private final ModuleCategory category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final KeyBindSetting keybind;

    private boolean enabled;

    protected Module(
            ModuleContext context,
            String id,
            String name,
            String description,
            ModuleCategory category,
            int defaultKeybind
    ) {
        this.context = Objects.requireNonNull(context, "context");
        this.id = requireId(id);
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.category = Objects.requireNonNull(category, "category");
        this.keybind = addSetting(new KeyBindSetting("keybind", "Keybind", "Key used to toggle this module", defaultKeybind));
    }

    public final ModuleContext context() {
        return context;
    }

    public final String id() {
        return id;
    }

    public final String name() {
        return name;
    }

    public final String description() {
        return description;
    }

    public final ModuleCategory category() {
        return category;
    }

    public final boolean enabled() {
        return enabled;
    }

    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public final KeyBindSetting keybind() {
        return keybind;
    }

    public final List<Setting<?>> settings() {
        return List.copyOf(settings);
    }

    public final <T extends Setting<?>> T addSetting(T setting) {
        settings.add(Objects.requireNonNull(setting, "setting"));
        return setting;
    }

    public String displaySuffix() {
        NumberSetting minCps = findSetting("min_cps", NumberSetting.class);
        NumberSetting maxCps = findSetting("max_cps", NumberSetting.class);
        if (minCps != null && maxCps != null) {
            double min = minCps.value();
            double max = Math.max(min, maxCps.value());
            return "CPS " + formatNumber(min) + " - " + formatNumber(max);
        }
        return null;
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected final void trace(String message) {
        trace("module", message);
    }

    protected final void trace(String channel, String message) {
        context.trace().trace(this, channel, message);
    }

    final void enableInternal() {
        onEnable();
    }

    final void disableInternal() {
        onDisable();
    }

    private <T extends Setting<?>> T findSetting(String settingId, Class<T> type) {
        for (Setting<?> setting : settings) {
            if (setting.id().equals(settingId) && type.isInstance(setting)) {
                return type.cast(setting);
            }
        }
        return null;
    }

    private static String formatNumber(double value) {
        return value == (long) value ? Long.toString((long) value) : String.format("%.2f", value);
    }

    private static String requireId(String id) {
        Objects.requireNonNull(id, "id");
        String normalized = id.trim().toLowerCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Module id cannot be blank");
        }
        return normalized;
    }
}
