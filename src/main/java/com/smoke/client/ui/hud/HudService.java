package com.smoke.client.ui.hud;

import com.smoke.client.bootstrap.ClientRuntime;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.List;

public final class HudService {
    private static final long TOAST_TTL_MS = 2500L;
    private final List<HudElement> elements = new ArrayList<>();
    private final List<Toast> toasts = new ArrayList<>();
    private boolean overlayEnabled = true;

    public void registerDefaults(ClientRuntime runtime) {
        elements.clear();
        elements.add(new ArrayListHudElement());
        elements.add(new InfoHudElement());
        elements.add(new RotationHudElement());
        elements.add(new TraceHudElement());
    }

    public void render(DrawContext drawContext, RenderTickCounter tickCounter, ClientRuntime runtime) {
        if (overlayEnabled) {
            for (HudElement element : elements) {
                element.render(drawContext, tickCounter, runtime);
            }
        }
        renderToasts(drawContext, runtime);
    }

    public void setOverlayEnabled(boolean overlayEnabled) {
        this.overlayEnabled = overlayEnabled;
    }

    public void pushToast(String message) {
        if (message == null || message.isBlank()) return;
        toasts.add(new Toast(message.trim(), System.currentTimeMillis() + TOAST_TTL_MS));
    }

    private void renderToasts(DrawContext drawContext, ClientRuntime runtime) {
        long now = System.currentTimeMillis();
        toasts.removeIf(toast -> toast.expiresAt <= now);
        if (toasts.isEmpty()) return;
        TextRenderer textRenderer = runtime.theme().textRenderer();
        int y = drawContext.getScaledWindowHeight() - 28;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast toast = toasts.get(i);
            int width = textRenderer.getWidth(toast.message) + 12;
            int x = drawContext.getScaledWindowWidth() - width - 8;
            drawContext.fill(x - 1, y - 1, x + width + 1, y + 13, runtime.theme().backgroundColor());
            drawContext.fill(x, y, x + width, y + 12, runtime.theme().panelColor());
            drawContext.fill(x, y, x + 2, y + 12, runtime.theme().accentColor());
            drawContext.drawText(textRenderer, toast.message, x + 6, y + 2, runtime.theme().textColor(), true);
            y -= 16;
        }
    }

    private record Toast(String message, long expiresAt) {
    }
}
