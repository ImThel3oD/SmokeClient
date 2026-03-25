package com.smoke.client.command.impl;

import com.smoke.client.command.Command;
import com.smoke.client.command.CommandContext;
import com.smoke.client.module.Module;
import com.smoke.client.setting.KeyBindSetting;

public final class BindCommand implements Command {
    @Override
    public String name() {
        return "bind";
    }

    @Override
    public String description() {
        return "Sets a module keybind.";
    }

    @Override
    public void execute(CommandContext context) {
        if (context.args().size() < 2) {
            context.reply("Usage: .bind <module> <key|none>");
            return;
        }

        Module module = context.runtime().moduleManager().getById(context.args().get(0))
                .or(() -> context.runtime().moduleManager().getByName(context.args().get(0)))
                .orElse(null);
        if (module == null) {
            context.reply("Unknown module: " + context.args().get(0));
            return;
        }

        int keyCode = KeyBindSetting.parse(context.args().get(1));
        module.keybind().setValue(keyCode);
        context.reply(module.name() + " bind -> " + module.keybind().displayValue());
    }
}
