package com.smoke.client.command.impl;

import com.smoke.client.command.Command;
import com.smoke.client.command.CommandContext;
import com.smoke.client.module.Module;

public final class ToggleCommand implements Command {
    @Override
    public String name() {
        return "toggle";
    }

    @Override
    public String description() {
        return "Toggles a module by name or id.";
    }

    @Override
    public void execute(CommandContext context) {
        if (context.args().isEmpty()) {
            context.reply("Usage: .toggle <module>");
            return;
        }

        String token = context.args().get(0);
        Module module = context.runtime().moduleManager().getById(token)
                .or(() -> context.runtime().moduleManager().getByName(token))
                .orElse(null);
        if (module == null) {
            context.reply("Unknown module: " + token);
            return;
        }

        context.runtime().moduleManager().toggle(module);
        context.reply(module.name() + " -> " + (module.enabled() ? "enabled" : "disabled"));
    }
}
