package com.smoke.client.feature.module.render;

import com.smoke.client.module.ModuleContext;
import com.smoke.client.module.ModuleManager;

public final class RenderModules {
    private RenderModules() {
    }

    public static void register(ModuleManager moduleManager, ModuleContext moduleContext) {
        moduleManager.register(new HudModule(moduleContext));
        moduleManager.register(new InfoModule(moduleContext));
        moduleManager.register(new TargetHudModule(moduleContext));
        moduleManager.register(new BedEspModule(moduleContext));
        moduleManager.register(new ChestEspModule(moduleContext));
        moduleManager.register(new EspModule(moduleContext));
        moduleManager.register(new TracersModule(moduleContext));
        moduleManager.register(new FullBrightModule(moduleContext));
        moduleManager.register(new NameTagsModule(moduleContext));
    }
}
