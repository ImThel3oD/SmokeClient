package com.smoke.client.ui.hud;

import com.smoke.client.bootstrap.ClientRuntime;
import com.smoke.client.trace.ModuleTraceService;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.List;

public final class TraceHudElement implements HudElement {
    private static final int PADDING_X = 5;
    private static final int PADDING_Y = 4;
    private static final int BORDER = 2;
    private static final int BACKGROUND = 0xB0141822;

    @Override
    public String id() {
        return "trace";
    }

    @Override
    public void render(DrawContext drawContext, RenderTickCounter tickCounter, ClientRuntime runtime) {
        ModuleTraceService.TraceView view = runtime.moduleTraceService().view();
        if (!view.visible()) {
            return;
        }

        TextRenderer textRenderer = runtime.theme().textRenderer();
        int maxTextWidth = Math.max(96, drawContext.getScaledWindowWidth() - HudLayout.LEFT_X - 16);
        List<String> lines = new ArrayList<>();
        lines.add(trimToWidth(textRenderer, view.header(), maxTextWidth));
        for (ModuleTraceService.TraceEntry entry : view.entries()) {
            lines.add(trimToWidth(textRenderer, entry.hudLine(), maxTextWidth));
        }

        int x = HudLayout.LEFT_X;
        int y = HudLayout.belowRotation(runtime);
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, textRenderer.getWidth(line));
        }

        int panelWidth = width + PADDING_X * 2;
        int panelHeight = lines.size() * HudLayout.LINE_HEIGHT + PADDING_Y * 2;
        drawContext.fill(x - BORDER, y - BORDER, x + panelWidth + BORDER, y + panelHeight + BORDER, runtime.theme().backgroundColor());
        drawContext.fill(x - BORDER, y - BORDER, x + panelWidth + BORDER, y + panelHeight + BORDER, BACKGROUND);
        drawContext.fill(x - BORDER, y - BORDER, x + panelWidth + BORDER, y - BORDER + 2, runtime.theme().accentColor());

        int drawY = y + PADDING_Y;
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? runtime.theme().accentColor() : runtime.theme().textColor();
            drawContext.drawText(textRenderer, lines.get(i), x + PADDING_X, drawY, color, true);
            drawY += HudLayout.LINE_HEIGHT;
        }
    }

    private static String trimToWidth(TextRenderer textRenderer, String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int length = text.length();
        while (length > 0) {
            String candidate = text.substring(0, length) + ellipsis;
            if (textRenderer.getWidth(candidate) <= maxWidth) {
                return candidate;
            }
            length--;
        }
        return ellipsis;
    }
}
