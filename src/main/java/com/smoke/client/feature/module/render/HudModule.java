package com.smoke.client.feature.module.render;

import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import org.lwjgl.glfw.GLFW;

public final class HudModule extends Module {
    public HudModule(ModuleContext context) {
        super(context, "hud", "HUD", "Toggles the shared HUD overlay.", ModuleCategory.RENDER, GLFW.GLFW_KEY_UNKNOWN);
        setEnabled(true);
        context.hud().setOverlayEnabled(true);
    }

    @Override
    protected void onEnable() {
        context().hud().setOverlayEnabled(true);
    }

    @Override
    protected void onDisable() {
        context().hud().setOverlayEnabled(false);
    }
}
