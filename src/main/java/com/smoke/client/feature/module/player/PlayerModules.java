package com.smoke.client.feature.module.player;

import com.smoke.client.module.ModuleContext;
import com.smoke.client.module.ModuleManager;

public final class PlayerModules {
    private PlayerModules() {
    }

    public static void register(ModuleManager moduleManager, ModuleContext moduleContext) {
        moduleManager.register(new AutoToolModule(moduleContext));
        moduleManager.register(new FakeLagModule(moduleContext));
        moduleManager.register(new InvCleanerModule(moduleContext));
    }
}
