package com.smoke.client.ui.click;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

final class ClickGuiPaint {
    private ClickGuiPaint() {
    }

    static void drawBackdropBlur(DrawContext drawContext, ClickGuiLayout.Rect rect, float strength) {
        float clamped = Math.max(0.0F, Math.min(1.0F, strength));
        if (clamped <= 0.0F) {
            return;
        }

        for (int step = 5; step >= 1; step--) {
            int inset = step * 6;
            int alpha = Math.max(0, Math.round((16 + (step * 7)) * clamped));
            fillRounded(
                    drawContext,
                    expand(rect, inset),
                    ClickGuiPalette.WINDOW_RADIUS + inset,
                    ClickGuiPalette.withAlpha(0xFF141414, alpha)
            );
        }
    }

    static void drawShadow(DrawContext drawContext, ClickGuiLayout.Rect rect, int radius, int color) {
        int baseAlpha = (color >>> 24) & 0xFF;
        if (baseAlpha <= 0 || radius <= 0) {
            return;
        }

        for (int step = radius; step >= 1; step--) {
            int alpha = Math.max(0, (int) Math.round(baseAlpha * ((radius - step + 1) / (double) (radius + 1)) * 0.32D));
            if (alpha <= 0) {
                continue;
            }
            fillRounded(
                    drawContext,
                    expand(rect, step),
                    ClickGuiPalette.WINDOW_RADIUS + step,
                    ClickGuiPalette.withAlpha(color, alpha)
            );
        }
    }

    static void drawPanel(DrawContext drawContext, ClickGuiLayout.Rect rect, int topColor, int bottomColor, int borderColor, int highlightColor) {
        drawPanel(drawContext, rect, topColor, bottomColor, borderColor, highlightColor, ClickGuiPalette.PANEL_RADIUS);
    }

    static void drawPanel(
            DrawContext drawContext,
            ClickGuiLayout.Rect rect,
            int topColor,
            int bottomColor,
            int borderColor,
            int highlightColor,
            int radius
    ) {
        if (((borderColor >>> 24) & 0xFF) > 0) {
            fillRounded(drawContext, rect, radius, borderColor);
        }

        ClickGuiLayout.Rect inner = inset(rect, 1);
        int innerRadius = Math.max(2, radius - 1);
        fillRoundedGradient(drawContext, inner, innerRadius, topColor, bottomColor);

        if (((highlightColor >>> 24) & 0xFF) > 0 && inner.width() > 8 && inner.height() > 5) {
            ClickGuiLayout.Rect topHighlight = new ClickGuiLayout.Rect(inner.x(), inner.y(), inner.width(), Math.min(4, inner.height()));
            fillRoundedGradient(
                    drawContext,
                    topHighlight,
                    innerRadius,
                    highlightColor,
                    ClickGuiPalette.withAlpha(highlightColor, 0)
            );
        }
    }

    static int badgeWidth(TextRenderer textRenderer, Text label) {
        return Math.max(18, textRenderer.getWidth(label) + 12);
    }

    static void drawBadge(
            DrawContext drawContext,
            TextRenderer textRenderer,
            ClickGuiLayout.Rect rect,
            Text label,
            int fillColor,
            int borderColor,
            int textColor
    ) {
        drawPanel(drawContext, rect, fillColor, fillColor, borderColor, ClickGuiPalette.withAlpha(ClickGuiPalette.TEXT_PRIMARY, 12), ClickGuiPalette.BADGE_RADIUS);
        drawContext.drawText(
                textRenderer,
                label,
                rect.x() + (rect.width() - textRenderer.getWidth(label)) / 2,
                rect.y() + Math.max(2, (rect.height() - textRenderer.fontHeight) / 2),
                textColor,
                false
        );
    }

    static int mix(int from, int to, float amount) {
        float clamped = Math.max(0.0F, Math.min(1.0F, amount));
        int fromA = (from >>> 24) & 0xFF;
        int fromR = (from >>> 16) & 0xFF;
        int fromG = (from >>> 8) & 0xFF;
        int fromB = from & 0xFF;
        int toA = (to >>> 24) & 0xFF;
        int toR = (to >>> 16) & 0xFF;
        int toG = (to >>> 8) & 0xFF;
        int toB = to & 0xFF;

        int alpha = (int) Math.round(fromA + ((toA - fromA) * clamped));
        int red = (int) Math.round(fromR + ((toR - fromR) * clamped));
        int green = (int) Math.round(fromG + ((toG - fromG) * clamped));
        int blue = (int) Math.round(fromB + ((toB - fromB) * clamped));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static void fillRoundedGradient(DrawContext drawContext, ClickGuiLayout.Rect rect, int radius, int topColor, int bottomColor) {
        int height = rect.height();
        if (height <= 0) {
            return;
        }

        for (int row = 0; row < height; row++) {
            float progress = height <= 1 ? 0.0F : row / (float) (height - 1);
            int color = mix(topColor, bottomColor, progress);
            int inset = roundedInset(row, height, radius);
            drawContext.fill(rect.x() + inset, rect.y() + row, rect.right() - inset, rect.y() + row + 1, color);
        }
    }

    private static void fillRounded(DrawContext drawContext, ClickGuiLayout.Rect rect, int radius, int color) {
        fillRoundedGradient(drawContext, rect, radius, color, color);
    }

    private static int roundedInset(int row, int height, int radius) {
        int distanceFromEdge = Math.min(row, height - 1 - row);
        if (distanceFromEdge >= radius || radius <= 1) {
            return 0;
        }

        double y = radius - distanceFromEdge - 0.5D;
        return Math.max(0, (int) Math.floor(radius - Math.sqrt(Math.max(0.0D, (radius * radius) - (y * y)))));
    }

    private static ClickGuiLayout.Rect inset(ClickGuiLayout.Rect rect, int amount) {
        return new ClickGuiLayout.Rect(
                rect.x() + amount,
                rect.y() + amount,
                Math.max(0, rect.width() - (amount * 2)),
                Math.max(0, rect.height() - (amount * 2))
        );
    }

    static void drawGlow(DrawContext drawContext, ClickGuiLayout.Rect rect, int radius, int color) {
        int baseAlpha = (color >>> 24) & 0xFF;
        if (baseAlpha <= 0 || radius <= 0) {
            return;
        }

        for (int step = radius; step >= 1; step--) {
            int alpha = Math.max(0, (int) Math.round(baseAlpha * ((radius - step + 1) / (double) (radius + 2)) * 0.24D));
            if (alpha <= 0) {
                continue;
            }
            fillRounded(
                    drawContext,
                    expand(rect, step),
                    ClickGuiPalette.CONTROL_RADIUS + step,
                    ClickGuiPalette.withAlpha(color, alpha)
            );
        }
    }

    private static ClickGuiLayout.Rect expand(ClickGuiLayout.Rect rect, int amount) {
        return new ClickGuiLayout.Rect(
                rect.x() - amount,
                rect.y() - amount,
                rect.width() + (amount * 2),
                rect.height() + (amount * 2)
        );
    }
}
