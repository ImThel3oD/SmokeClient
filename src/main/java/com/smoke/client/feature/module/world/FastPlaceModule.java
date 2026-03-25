package com.smoke.client.feature.module.world;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.mixin.accessor.MinecraftClientAccessor;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public final class FastPlaceModule extends Module {
    private final NumberSetting delay = addSetting(new NumberSetting(
            "delay",
            "Delay",
            "Ticks between use actions while holding use.",
            1.0D,
            0.0D,
            4.0D,
            1.0D
    ));

    public FastPlaceModule(ModuleContext context) {
        super(context, "fast_place", "FastPlace", "Reduces the vanilla item use cooldown to place blocks faster.", ModuleCategory.WORLD, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (event.phase() != TickEvent.Phase.PRE) {
            return;
        }

        MinecraftClientAccessor client = (MinecraftClientAccessor) (Object) MinecraftClient.getInstance();
        client.smoke$setItemUseCooldown(Math.min(client.smoke$getItemUseCooldown(), delay.value().intValue() + 1));
    }
}
