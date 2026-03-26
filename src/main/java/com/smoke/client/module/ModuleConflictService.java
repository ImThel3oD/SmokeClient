package com.smoke.client.module;

import com.smoke.client.bootstrap.ClientRuntime;
import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.ModuleToggleEvent;

public final class ModuleConflictService {
    private final ClientRuntime runtime;

    public ModuleConflictService(ClientRuntime runtime) {
        this.runtime = runtime;
    }

    @Subscribe
    private void onModuleToggle(ModuleToggleEvent event) {
        if (!event.enabled()) return;
        String otherId = switch (event.module().id()) {
            case "jump_reset" -> "velocity";
            case "velocity" -> "jump_reset";
            default -> null;
        };
        Module other = otherId == null ? null : runtime.moduleManager().getById(otherId).orElse(null);
        if (other == null || !other.enabled()) return;
        runtime.moduleManager().setEnabled(event.module(), false);
        runtime.hudService().pushToast(event.module().name() + " cannot be enabled while " + other.name() + " is active.");
    }
}
