package com.smoke.client.ui.theme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;

public record Theme(int backgroundColor, int panelColor, int accentColor, int textColor) {
    public static Theme defaultTheme() {
        return new Theme(0xCC11141B, 0xF01A1F2A, 0xFF78C6FF, 0xFFFFFFFF);
    }

    public TextRenderer textRenderer() {
        return MinecraftClient.getInstance().textRenderer;
    }
}
