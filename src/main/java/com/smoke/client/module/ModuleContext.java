package com.smoke.client.module;

import com.smoke.client.bootstrap.ClientRuntime;
import com.smoke.client.command.CommandDispatcher;
import com.smoke.client.config.ConfigService;
import com.smoke.client.event.EventBus;
import com.smoke.client.input.InputService;
import com.smoke.client.network.PacketService;
import com.smoke.client.rotation.RotationService;
import com.smoke.client.trace.ModuleTraceService;
import com.smoke.client.ui.hud.HudService;

public final class ModuleContext {
    private final ClientRuntime runtime;

    public ModuleContext(ClientRuntime runtime) {
        this.runtime = runtime;
    }

    public ClientRuntime runtime() {
        return runtime;
    }

    public EventBus events() {
        return runtime.eventBus();
    }

    public ModuleManager modules() {
        return runtime.moduleManager();
    }

    public RotationService rotation() {
        return runtime.rotationService();
    }

    public PacketService packets() {
        return runtime.packetService();
    }

    public ConfigService config() {
        return runtime.configService();
    }

    public InputService input() {
        return runtime.inputService();
    }

    public CommandDispatcher commands() {
        return runtime.commandDispatcher();
    }

    public HudService hud() {
        return runtime.hudService();
    }

    public ModuleTraceService trace() {
        return runtime.moduleTraceService();
    }

    public void feedback(String message) {
        runtime.sendChatFeedback(message);
    }
}
