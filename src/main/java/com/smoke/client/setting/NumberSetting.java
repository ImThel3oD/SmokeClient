package com.smoke.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String id, String label, String description, double defaultValue, double min, double max, double step) {
        super(id, label, description, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step <= 0.0D ? 0.0D : step;
        setValue(defaultValue);
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            setValue(element.getAsDouble());
        }
    }

    @Override
    public String displayValue() {
        double value = value();
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return String.format("%.2f", value);
    }

    @Override
    protected Double sanitize(Double value) {
        double clamped = Math.max(min, Math.min(max, value));
        if (step > 0.0D) {
            double snapped = Math.round((clamped - min) / step) * step + min;
            clamped = Math.max(min, Math.min(max, snapped));
        }
        return clamped;
    }
}
