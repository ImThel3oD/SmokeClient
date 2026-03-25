package com.smoke.client.ui.hud;

import com.smoke.client.bootstrap.ClientRuntime;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public interface HudElement {
    String id();

    void render(DrawContext drawContext, RenderTickCounter tickCounter, ClientRuntime runtime);
}
