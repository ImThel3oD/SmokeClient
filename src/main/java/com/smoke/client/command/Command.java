package com.smoke.client.command;

import java.util.List;

public interface Command {
    String name();

    default List<String> aliases() {
        return List.of();
    }

    String description();

    void execute(CommandContext context);
}
