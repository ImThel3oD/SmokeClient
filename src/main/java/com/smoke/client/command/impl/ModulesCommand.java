package com.smoke.client.command.impl;

import com.smoke.client.command.Command;
import com.smoke.client.command.CommandContext;
import com.smoke.client.module.Module;

public final class ModulesCommand implements Command {
    @Override
    public String name() {
        return "modules";
    }

    @Override
    public String description() {
        return "Lists all registered modules.";
    }

    @Override
    public void execute(CommandContext context) {
        for (Module module : context.runtime().moduleManager().all()) {
            context.reply(module.name() + " [" + module.id() + "] - " + (module.enabled() ? "enabled" : "disabled"));
        }
    }
}
