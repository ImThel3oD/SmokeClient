package com.smoke.client.setting;

import com.google.gson.JsonElement;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
    private final String id;
    private final String label;
    private final String description;
    private final T defaultValue;
    private BooleanSupplier visibility = () -> true;
    private T value;

    protected Setting(String id, String label, String description, T defaultValue) {
        this.id = normalizeId(id);
        this.label = Objects.requireNonNull(label, "label");
        this.description = Objects.requireNonNull(description, "description");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.value = defaultValue;
    }

    public final String id() {
        return id;
    }

    public final String label() {
        return label;
    }

    public final String description() {
        return description;
    }

    public final T defaultValue() {
        return defaultValue;
    }

    public final T value() {
        return value;
    }

    public final void setValue(T value) {
        this.value = sanitize(Objects.requireNonNull(value, "value"));
    }

    public final void reset() {
        this.value = defaultValue;
    }

    public final boolean visible() {
        return visibility.getAsBoolean();
    }

    public final Setting<T> visibleWhen(BooleanSupplier visibility) {
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        return this;
    }

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement element);

    public String displayValue() {
        return String.valueOf(value);
    }

    protected T sanitize(T value) {
        return value;
    }

    private static String normalizeId(String id) {
        Objects.requireNonNull(id, "id");
        String normalized = id.trim().toLowerCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Setting id cannot be blank");
        }
        return normalized;
    }
}
