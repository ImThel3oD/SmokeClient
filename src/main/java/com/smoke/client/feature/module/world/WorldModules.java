package com.smoke.client.feature.module.world;

import com.smoke.client.module.ModuleContext;
import com.smoke.client.module.ModuleManager;

public final class WorldModules {
    private WorldModules() {
    }

    public static void register(ModuleManager moduleManager, ModuleContext moduleContext) {
        moduleManager.register(new FastPlaceModule(moduleContext));
        moduleManager.register(new ChestStealer(moduleContext));
        moduleManager.register(new ScaffoldModule(moduleContext));
        moduleManager.register(new MLG(moduleContext));
    }
}
