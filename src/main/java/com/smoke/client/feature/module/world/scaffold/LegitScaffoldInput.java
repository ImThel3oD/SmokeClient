package com.smoke.client.feature.module.world.scaffold;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Minimal helper for forcing sneak via vanilla keybindings without desyncing the user's real key state.
 */
public final class LegitScaffoldInput {
    private boolean forcedSneak;

    public void forceSneak(boolean forced) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) {
            return;
        }

        KeyBinding sneak = client.options.sneakKey;
        if (forced) {
            forcedSneak = true;
            sneak.setPressed(true);
            return;
        }

        if (!forcedSneak) {
            return;
        }

        forcedSneak = false;
        sneak.setPressed(isPhysicallyDown(client, sneak));
    }

    public void reset() {
        forceSneak(false);
    }

    private static boolean isPhysicallyDown(MinecraftClient client, KeyBinding binding) {
        InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
        if (key == null || key == InputUtil.UNKNOWN_KEY) {
            return false;
        }

        long handle = client.getWindow().getHandle();
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(handle, key.getCode()) == GLFW.GLFW_PRESS;
        }
        if (key.getCategory() == InputUtil.Type.KEYSYM) {
            return InputUtil.isKeyPressed(handle, key.getCode());
        }

        return false;
    }
}
