package com.smoke.client.feature.module.combat;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.HandleInputEvent;
import com.smoke.client.mixin.accessor.MinecraftClientAccessor;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

public final class AutoClickerModule extends Module {
    private final NumberSetting minCps = addSetting(new NumberSetting("min_cps", "CPS Min", "Minimum clicks per second.", 9.0D, 1.0D, 20.0D, 1.0D));
    private final NumberSetting maxCps = addSetting(new NumberSetting("max_cps", "CPS Max", "Maximum clicks per second.", 13.0D, 1.0D, 20.0D, 1.0D));
    private long lastClickMs;
    private long delayMs;

    public AutoClickerModule(ModuleContext context) {
        super(context, "auto_clicker", "AutoClicker", "Automatically performs vanilla left clicks while attack is held.", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    protected void onDisable() {
        reset();
    }

    @Subscribe
    private void onHandleInput(HandleInputEvent event) {
        if (event.phase() != HandleInputEvent.Phase.POST) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.interactionManager == null || client.options == null
                || client.crosshairTarget == null || client.currentScreen != null || client.getOverlay() != null
                || !client.options.attackKey.isPressed()) { reset(); return; }
        long now = System.currentTimeMillis();
        if (now - lastClickMs < delayMs) return;
        ((MinecraftClientAccessor) (Object) client).smoke$doAttack();
        lastClickMs = now;
        delayMs = nextDelayMs();
    }

    private long nextDelayMs() {
        double min = minCps.value(), max = Math.max(min, maxCps.value());
        double cps = ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max));
        return Math.max(1L, Math.round(1000.0D / cps));
    }

    private void reset() {
        lastClickMs = 0L;
        delayMs = 0L;
    }
}
