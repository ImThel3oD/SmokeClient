package com.smoke.client.input;

/**
 * Marker for screens that should suppress module keybind polling while open.
 */
public interface BlocksModuleKeybindsScreen {
    default boolean allowClientHotkey(int keyCode) {
        return true;
    }
}
