package com.smoke.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class ColorSetting extends Setting<Integer> {
    public ColorSetting(String id, String label, String description, int defaultValue) {
        super(id, label, description, defaultValue);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            setValue(element.getAsInt());
        }
    }

    @Override
    public String displayValue() {
        return String.format("#%08X", value());
    }

    public void rotateHue(float degrees) {
        int argb = value();
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;

        float[] hsb = java.awt.Color.RGBtoHSB(red, green, blue, null);
        float hue = (hsb[0] + (degrees / 360.0F)) % 1.0F;
        if (hue < 0.0F) {
            hue += 1.0F;
        }

        int rgb = java.awt.Color.HSBtoRGB(hue, Math.max(0.35F, hsb[1]), Math.max(0.35F, hsb[2])) & 0x00FFFFFF;
        setValue((alpha << 24) | rgb);
    }
}
