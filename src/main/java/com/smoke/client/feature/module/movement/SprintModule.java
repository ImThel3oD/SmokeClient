package com.smoke.client.feature.module.movement;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.BoolSetting;
import org.lwjgl.glfw.GLFW;

public final class SprintModule extends Module {
    private final BoolSetting omni = addSetting(new BoolSetting("omni", "Omni Sprint", "Allow sprint while moving in any direction.", true));

    public SprintModule(ModuleContext context) {
        super(context, "sprint", "Sprint", "Keeps sprint active while moving.", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_G);
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (event.phase() != TickEvent.Phase.PRE) {
            return;
        }

        if (net.minecraft.client.MinecraftClient.getInstance().player != null) {
            net.minecraft.client.MinecraftClient.getInstance().player.setSprinting(
                    omni.value() || net.minecraft.client.MinecraftClient.getInstance().player.forwardSpeed > 0.0F
            );
        }
    }
}
