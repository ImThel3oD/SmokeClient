package com.smoke.client.feature.module.movement;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

public final class InvMoveModule extends Module {
    private final Set<KeyBinding> held = new HashSet<>();

    public InvMoveModule(ModuleContext context) {
        super(context, "inv_move", "InvMove", "Allows movement inside inventory screens.", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    protected void onDisable() {
        if (MinecraftClient.getInstance().currentScreen != null) releaseAll();
        else held.clear();
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (event.phase() != TickEvent.Phase.PRE) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null || client.getWindow() == null) return;
        if (!shouldHandle(client.currentScreen)) {
            if (client.currentScreen != null) releaseAll();
            return;
        }
        sync(client, client.options.forwardKey);
        sync(client, client.options.backKey);
        sync(client, client.options.leftKey);
        sync(client, client.options.rightKey);
        sync(client, client.options.jumpKey);
        sync(client, client.options.sneakKey);
        sync(client, client.options.sprintKey);
    }

    private void sync(MinecraftClient client, KeyBinding binding) {
        boolean down = isDown(client, binding);
        if (down == held.contains(binding)) return;
        if (down) held.add(binding);
        else held.remove(binding);
        binding.setPressed(down);
    }

    private void releaseAll() {
        held.forEach(binding -> binding.setPressed(false));
        held.clear();
    }

    private static boolean shouldHandle(Screen screen) {
        return screen instanceof HandledScreen<?> handled && !(handled.getFocused() instanceof TextFieldWidget);
    }

    private static boolean isDown(MinecraftClient client, KeyBinding binding) {
        InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
        if (key == null || key == InputUtil.UNKNOWN_KEY) return false;
        long handle = client.getWindow().getHandle();
        if (key.getCategory() == InputUtil.Type.MOUSE) return GLFW.glfwGetMouseButton(handle, key.getCode()) == GLFW.GLFW_PRESS;
        return key.getCategory() == InputUtil.Type.KEYSYM && InputUtil.isKeyPressed(handle, key.getCode());
    }
}
