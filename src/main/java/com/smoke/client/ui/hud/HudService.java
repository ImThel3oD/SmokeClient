package com.smoke.client.ui.hud;

import com.smoke.client.bootstrap.ClientRuntime;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.List;

public final class HudService {
    private final List<HudElement> elements = new ArrayList<>();
    private boolean overlayEnabled = true;

    public void registerDefaults(ClientRuntime runtime) {
        elements.clear();
        elements.add(new ArrayListHudElement());
        elements.add(new InfoHudElement());
        elements.add(new RotationHudElement());
        elements.add(new TraceHudElement());
    }

    public void render(DrawContext drawContext, RenderTickCounter tickCounter, ClientRuntime runtime) {
        if (!overlayEnabled) {
            return;
        }
        for (HudElement element : elements) {
            element.render(drawContext, tickCounter, runtime);
        }
    }

    public void setOverlayEnabled(boolean overlayEnabled) {
        this.overlayEnabled = overlayEnabled;
    }
}
