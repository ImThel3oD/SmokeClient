package com.smoke.client.feature.module.render;

import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import org.lwjgl.glfw.GLFW;

public final class InfoModule extends Module {
    public InfoModule(ModuleContext context) {
        super(context, "info", "Info", "Displays FPS, range, rotation, and coordinates under the array list.", ModuleCategory.RENDER, GLFW.GLFW_KEY_UNKNOWN);
        setEnabled(true);
    }
}

