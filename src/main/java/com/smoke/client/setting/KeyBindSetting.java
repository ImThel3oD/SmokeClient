package com.smoke.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class KeyBindSetting extends Setting<Integer> {
    public KeyBindSetting(String id, String label, String description, int defaultValue) {
        super(id, label, description, defaultValue);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            setValue(element.getAsInt());
        }
    }

    @Override
    public String displayValue() {
        return keyName(value());
    }

    public static String keyName(int keyCode) {
        if (keyCode <= 0 || keyCode == GLFW.GLFW_KEY_UNKNOWN) {
            return "NONE";
        }

        String keyName = GLFW.glfwGetKeyName(keyCode, 0);
        if (keyName != null) {
            return keyName.toUpperCase(Locale.ROOT);
        }

        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_ESCAPE -> "ESC";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            default -> {
                if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F25) {
                    yield "F" + (keyCode - GLFW.GLFW_KEY_F1 + 1);
                }
                yield "KEY_" + keyCode;
            }
        };
    }

    public static int parse(String token) {
        if (token == null || token.isBlank()) {
            return GLFW.GLFW_KEY_UNKNOWN;
        }

        String normalized = token.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("NONE")) {
            return GLFW.GLFW_KEY_UNKNOWN;
        }

        if (normalized.length() == 1) {
            char character = normalized.charAt(0);
            if (character >= 'A' && character <= 'Z') {
                return GLFW.GLFW_KEY_A + (character - 'A');
            }
            if (character >= '0' && character <= '9') {
                return GLFW.GLFW_KEY_0 + (character - '0');
            }
        }

        if (normalized.startsWith("F")) {
            try {
                int fKey = Integer.parseInt(normalized.substring(1));
                if (fKey >= 1 && fKey <= 25) {
                    return GLFW.GLFW_KEY_F1 + (fKey - 1);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return switch (normalized) {
            case "SPACE" -> GLFW.GLFW_KEY_SPACE;
            case "RSHIFT" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "LSHIFT" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "RCTRL" -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "LCTRL" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "RALT" -> GLFW.GLFW_KEY_RIGHT_ALT;
            case "LALT" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "TAB" -> GLFW.GLFW_KEY_TAB;
            case "ENTER" -> GLFW.GLFW_KEY_ENTER;
            case "ESC", "ESCAPE" -> GLFW.GLFW_KEY_ESCAPE;
            case "UP" -> GLFW.GLFW_KEY_UP;
            case "DOWN" -> GLFW.GLFW_KEY_DOWN;
            case "LEFT" -> GLFW.GLFW_KEY_LEFT;
            case "RIGHT" -> GLFW.GLFW_KEY_RIGHT;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
    }
}
