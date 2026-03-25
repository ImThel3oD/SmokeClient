package com.smoke.client.feature.module.combat;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;

public final class WTapModule extends Module {
    private boolean released;
    public WTapModule(ModuleContext context) { super(context, "w_tap", "WTap", "Releases sprint for one tick after sprint hits.", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN); }

    public void trigger(PlayerEntity player, Entity target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(target instanceof LivingEntity living) || !living.isAlive() || target.isRemoved() || client.options == null || !player.isSprinting()) return;
        client.options.sprintKey.setPressed(false);
        released = true;
    }

    @Subscribe
    private void onTick(TickEvent event) { if (event.phase() == TickEvent.Phase.PRE && released) restore(); }

    @Override
    protected void onDisable() { restore(); }

    private void restore() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) client.options.sprintKey.setPressed(isPhysicallyDown(client, client.options.sprintKey));
        released = false;
    }

    private static boolean isPhysicallyDown(MinecraftClient client, KeyBinding binding) {
        InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
        if (client.getWindow() == null || key == null || key == InputUtil.UNKNOWN_KEY) return false;
        long handle = client.getWindow().getHandle();
        return key.getCategory() == InputUtil.Type.MOUSE ? GLFW.glfwGetMouseButton(handle, key.getCode()) == GLFW.GLFW_PRESS : key.getCategory() == InputUtil.Type.KEYSYM && InputUtil.isKeyPressed(handle, key.getCode());
    }
}
