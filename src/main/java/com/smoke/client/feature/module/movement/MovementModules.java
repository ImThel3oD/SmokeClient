package com.smoke.client.feature.module.movement;

import com.smoke.client.module.ModuleContext;
import com.smoke.client.module.ModuleManager;

public final class MovementModules {
    private MovementModules() {
    }

    public static void register(ModuleManager moduleManager, ModuleContext moduleContext) {
        moduleManager.register(new InvMoveModule(moduleContext));
        moduleManager.register(new SprintModule(moduleContext));
        moduleManager.register(new SafeWalkModule(moduleContext));
    }
}
