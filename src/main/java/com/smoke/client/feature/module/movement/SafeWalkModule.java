package com.smoke.client.feature.module.movement;

import com.smoke.client.mixin.accessor.InputAccessor;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.BoolSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import org.lwjgl.glfw.GLFW;

public final class SafeWalkModule extends Module {
    private final BoolSetting direction = addSetting(new BoolSetting("direction", "Direction", "Allow forward movement off edges.", true));

    public SafeWalkModule(ModuleContext context) {
        super(context, "safe_walk", "SafeWalk", "Stops the player from walking off edges.", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
    }

    public boolean shouldForceClip(ClientPlayerEntity player) {
        return enabled() && (!direction.value() || ((InputAccessor) player.input).smoke$getMovementVector().y <= 0.0F);
    }
}
