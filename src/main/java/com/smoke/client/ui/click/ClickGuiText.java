package com.smoke.client.ui.click;

import com.smoke.client.ui.font.SmokeFonts;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

final class ClickGuiText {
    private ClickGuiText() {
    }

    static List<String> wrapLines(TextRenderer textRenderer, String text, int maxWidth, ClickFont font) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (width(textRenderer, candidate, font) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                }
                current.setLength(0);
                current.append(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    static String trimToWidth(TextRenderer textRenderer, String text, int maxWidth, ClickFont font) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (width(textRenderer, text, font) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth = width(textRenderer, ellipsis, font);
        StringBuilder builder = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = builder.isEmpty() ? word : builder + " " + word;
            if (width(textRenderer, candidate, font) + ellipsisWidth > maxWidth) {
                break;
            }
            builder.setLength(0);
            builder.append(candidate);
        }
        if (!builder.isEmpty()) {
            return builder + ellipsis;
        }

        StringBuilder clipped = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            String candidate = clipped.toString() + text.charAt(index);
            if (width(textRenderer, candidate, font) + ellipsisWidth > maxWidth) {
                break;
            }
            clipped.append(text.charAt(index));
        }
        return clipped + ellipsis;
    }

    static int centeredTextY(ClickGuiLayout.Rect rect, ClickFont font) {
        return rect.y() + Math.max(2, (rect.height() - lineHeight(font)) / 2);
    }

    static void draw(DrawContext drawContext, TextRenderer textRenderer, String value, int x, int y, int color, ClickFont font) {
        drawContext.drawText(textRenderer, styled(value, font), x, y, color, false);
    }

    static int width(TextRenderer textRenderer, String value, ClickFont font) {
        return textRenderer.getWidth(styled(value, font));
    }

    static int lineHeight(ClickFont font) {
        return switch (font) {
            case TITLE -> 16;
            case BODY, THIN -> 10;
            case SMALL -> 9;
        };
    }

    private static Text styled(String value, ClickFont font) {
        return switch (font) {
            case TITLE -> SmokeFonts.venomTitle(value);
            case SMALL -> SmokeFonts.venomSmall(value);
            case THIN -> SmokeFonts.venomThin(value);
            case BODY -> SmokeFonts.venom(value);
        };
    }
}
