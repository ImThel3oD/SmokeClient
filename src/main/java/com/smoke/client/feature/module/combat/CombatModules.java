package com.smoke.client.feature.module.combat;

import com.smoke.client.module.ModuleContext;
import com.smoke.client.module.ModuleManager;

public final class CombatModules {
    private CombatModules() {
    }

    public static void register(ModuleManager moduleManager, ModuleContext moduleContext) {
        moduleManager.register(new AimAssistModule(moduleContext));
        moduleManager.register(new AntiBot(moduleContext));
        moduleManager.register(new AutoClickerModule(moduleContext));
        moduleManager.register(new BacktrackModule(moduleContext));
        moduleManager.register(new CriticalsModule(moduleContext));
        moduleManager.register(new KillAuraModule(moduleContext));
        moduleManager.register(new KnockbackDelayModule(moduleContext));
        moduleManager.register(new TriggerBotModule(moduleContext));
        moduleManager.register(new VelocityModule(moduleContext));
        moduleManager.register(new JumpResetModule(moduleContext));
        moduleManager.register(new KeepSprintModule(moduleContext));
        moduleManager.register(new WTapModule(moduleContext));
    }
}
