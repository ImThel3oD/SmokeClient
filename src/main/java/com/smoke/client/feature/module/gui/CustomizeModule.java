package com.smoke.client.feature.module.gui;

import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.ui.hud.CustomizeScreen;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public final class CustomizeModule extends Module {
    public CustomizeModule(ModuleContext context) {
        super(context, "customize", "Customize", "Opens the draggable HUD editor.", ModuleCategory.GUI, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    protected void onEnable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(new CustomizeScreen(context().runtime(), keybind().value()));
        }
        context().modules().setEnabled(this, false);
    }
}
