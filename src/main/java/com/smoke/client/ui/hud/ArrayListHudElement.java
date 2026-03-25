package com.smoke.client.ui.hud;

import com.smoke.client.bootstrap.ClientRuntime;
import com.smoke.client.module.Module;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public final class ArrayListHudElement implements HudElement {
    @Override
    public String id() {
        return "array_list";
    }

    @Override
    public void render(DrawContext drawContext, RenderTickCounter tickCounter, ClientRuntime runtime) {
        int y = 6;
        for (Module module : runtime.moduleManager().enabledModules()) {
            String suffix = module.displaySuffix();
            String line = suffix == null || suffix.isBlank() ? module.name() : module.name() + " [" + suffix + "]";
            drawContext.drawText(runtime.theme().textRenderer(), line, 6, y, runtime.theme().accentColor(), true);
            y += 10;
        }
    }
}
