package com.smoke.client.feature.module.gui;

import com.smoke.client.module.ModuleContext;
import com.smoke.client.module.ModuleManager;

public final class GuiModules {
    private GuiModules() {
    }

    public static void register(ModuleManager moduleManager, ModuleContext moduleContext) {
        moduleManager.register(new CustomizeModule(moduleContext));
    }
}
