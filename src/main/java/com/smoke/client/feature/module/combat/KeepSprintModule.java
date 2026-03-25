package com.smoke.client.feature.module.combat;

import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.NumberSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class KeepSprintModule extends Module {
    private final NumberSetting multiplier = addSetting(
            new NumberSetting("multiplier", "Multiplier", "Horizontal velocity kept after a sprint-hit (0.6 = vanilla).", 0.8, 0.1, 1.0, 0.05)
    );

    private boolean wasSprinting;

    public KeepSprintModule(ModuleContext context) {
        super(context, "keep_sprint", "KeepSprint", "Keeps sprint momentum after attacking.", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    public void beforeAttack(PlayerEntity player) {
        wasSprinting = player.isSprinting();
    }

    public void afterAttack(PlayerEntity player) {
        if (!wasSprinting) return;
        wasSprinting = false;
        double factor = multiplier.value() / 0.6;
        Vec3d vel = player.getVelocity();
        player.setVelocity(vel.x * factor, vel.y, vel.z * factor);
        player.setSprinting(true);
    }
}
