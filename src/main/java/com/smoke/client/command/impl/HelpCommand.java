package com.smoke.client.command.impl;

import com.smoke.client.command.Command;
import com.smoke.client.command.CommandContext;

public final class HelpCommand implements Command {
    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "Lists available commands.";
    }

    @Override
    public void execute(CommandContext context) {
        context.reply("Commands:");
        for (Command command : context.runtime().commandDispatcher().commands()) {
            context.reply("." + command.name() + " - " + command.description());
        }
    }
}
