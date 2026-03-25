package com.smoke.client.ui.hud;

import com.smoke.client.bootstrap.ClientRuntime;
import com.smoke.client.module.Module;

public final class HudLayout {
    public static final int LEFT_X = 6;
    public static final int TOP_Y = 6;
    public static final int LINE_HEIGHT = 10;
    public static final int INFO_LINES = 7;

    private HudLayout() {
    }

    public static int belowArrayList(ClientRuntime runtime) {
        int enabledModules = runtime.moduleManager().enabledModules().size();
        return TOP_Y + LINE_HEIGHT * (enabledModules + 1);
    }

    public static int belowInfo(ClientRuntime runtime) {
        int y = belowArrayList(runtime);
        if (isInfoEnabled(runtime)) {
            y += LINE_HEIGHT * INFO_LINES + LINE_HEIGHT;
        }
        return y;
    }

    public static int belowRotation(ClientRuntime runtime) {
        int y = belowInfo(runtime);
        if (runtime.rotationService().currentFrame().active()) {
            y += LINE_HEIGHT + LINE_HEIGHT;
        }
        return y;
    }

    public static boolean isInfoEnabled(ClientRuntime runtime) {
        return runtime.moduleManager()
                .getById("info")
                .map(Module::enabled)
                .orElse(false);
    }
}
