package com.smoke.client.feature.module.render;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.mixin.accessor.SimpleOptionAccessor;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import org.lwjgl.glfw.GLFW;

public final class FullBrightModule extends Module {
    private static final double FULL_BRIGHT_GAMMA = 100.0D;

    private Double previousGamma;

    public FullBrightModule(ModuleContext context) {
        super(context, "fullbright", "FullBright", "Maximizes brightness by forcing gamma.", ModuleCategory.RENDER, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    protected void onEnable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) {
            return;
        }

        SimpleOption<Double> gamma = client.options.getGamma();
        if (previousGamma == null) {
            previousGamma = gamma.getValue();
        }
        forceGamma(gamma, FULL_BRIGHT_GAMMA);
    }

    @Override
    protected void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) {
            previousGamma = null;
            return;
        }

        if (previousGamma != null) {
            forceGamma(client.options.getGamma(), previousGamma);
        }
        previousGamma = null;
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (event.phase() != TickEvent.Phase.PRE) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) {
            return;
        }

        SimpleOption<Double> gamma = client.options.getGamma();
        if (previousGamma == null) {
            previousGamma = gamma.getValue();
        }

        if (!Double.valueOf(FULL_BRIGHT_GAMMA).equals(gamma.getValue())) {
            forceGamma(gamma, FULL_BRIGHT_GAMMA);
        }
    }

    private static void forceGamma(SimpleOption<Double> gamma, double value) {
        ((SimpleOptionAccessor) (Object) gamma).smoke$setValue(value);
    }
}

