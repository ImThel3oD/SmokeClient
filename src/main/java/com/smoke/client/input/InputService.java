package com.smoke.client.input;

import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.util.InputUtil;

import java.util.HashSet;
import java.util.Set;

public final class InputService {
    private final Set<Integer> pressedKeys = new HashSet<>();

    public boolean consumePress(int keyCode, MinecraftClient client) {
        if (client.getWindow() == null) {
            return false;
        }
        return consumePress(keyCode, InputUtil.isKeyPressed(client.getWindow().getHandle(), keyCode));
    }

    public boolean consumePress(int keyCode, boolean isDown) {
        if (isDown) {
            return pressedKeys.add(keyCode);
        }
        pressedKeys.remove(keyCode);
        return false;
    }

    public void processModuleKeybinds(MinecraftClient client, ModuleManager moduleManager) {
        if (client.getWindow() == null) {
            return;
        }

        long handle = client.getWindow().getHandle();
        if (shouldBlockModuleBinds(client.currentScreen)) {
            for (Module module : moduleManager.all()) {
                int key = module.keybind().value();
                if (key > 0) {
                    consumePress(key, InputUtil.isKeyPressed(handle, key));
                }
            }
            return;
        }

        for (Module module : moduleManager.all()) {
            int key = module.keybind().value();
            if (key <= 0) {
                continue;
            }

            if (consumePress(key, InputUtil.isKeyPressed(handle, key))) {
                moduleManager.toggle(module);
            }
        }
    }

    public void clear() {
        pressedKeys.clear();
    }

    private static boolean shouldBlockModuleBinds(Screen screen) {
        if (screen == null) {
            return false;
        }
        if (screen instanceof BlocksModuleKeybindsScreen) {
            return true;
        }
        return screen instanceof ChatScreen
                || screen instanceof SignEditScreen
                || screen.shouldPause();
    }
}
