package com.smoke.client.alt;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

final class AltScreenPaint {
    private AltScreenPaint() {
    }

    static void drawButton(
            DrawContext context,
            TextRenderer textRenderer,
            AltRect rect,
            int mouseX,
            int mouseY,
            String label,
            int color,
            boolean enabled,
            int textColor,
            int dimTextColor
    ) {
        boolean hovered = enabled && rect.contains(mouseX, mouseY);
        int background = enabled ? (hovered ? lighten(color, 0.18F) : color) : 0xFF2D2D2D;
        context.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), background);
        drawBorder(context, rect, 0xFF111111);

        int labelWidth = textRenderer.getWidth(label);
        int textX = rect.x() + (rect.width() - labelWidth) / 2;
        int textY = rect.y() + 6;
        context.drawText(textRenderer, Text.literal(label), textX, textY, enabled ? textColor : dimTextColor, false);
    }

    static void drawBorder(DrawContext context, AltRect rect, int color) {
        context.fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, color);
        context.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), color);
        context.fill(rect.x(), rect.y(), rect.x() + 1, rect.bottom(), color);
        context.fill(rect.right() - 1, rect.y(), rect.right(), rect.bottom(), color);
    }

    private static int lighten(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.min(255, (int) (r + (255 - r) * factor));
        g = Math.min(255, (int) (g + (255 - g) * factor));
        b = Math.min(255, (int) (b + (255 - b) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
