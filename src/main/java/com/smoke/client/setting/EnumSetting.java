package com.smoke.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.Locale;

public final class EnumSetting<E extends Enum<E>> extends Setting<E> {
    private final Class<E> enumType;

    public EnumSetting(String id, String label, String description, Class<E> enumType, E defaultValue) {
        super(id, label, description, defaultValue);
        this.enumType = enumType;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value().name());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            String raw = element.getAsString();
            Arrays.stream(enumType.getEnumConstants())
                    .filter(constant -> constant.name().equalsIgnoreCase(raw))
                    .findFirst()
                    .ifPresent(this::setValue);
        }
    }

    @Override
    public String displayValue() {
        return value().name().toLowerCase(Locale.ROOT);
    }

    public void cycle(boolean forward) {
        E[] values = enumType.getEnumConstants();
        int currentIndex = value().ordinal();
        int nextIndex = forward
                ? (currentIndex + 1) % values.length
                : (currentIndex - 1 + values.length) % values.length;
        setValue(values[nextIndex]);
    }
}
