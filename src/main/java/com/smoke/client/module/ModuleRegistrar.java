package com.smoke.client.module;

@FunctionalInterface
public interface ModuleRegistrar {
    void register(ModuleManager moduleManager, ModuleContext moduleContext);
}
