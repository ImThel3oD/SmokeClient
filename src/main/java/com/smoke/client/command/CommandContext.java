package com.smoke.client.command;

import com.smoke.client.bootstrap.ClientRuntime;

import java.util.List;
import java.util.function.Consumer;

public record CommandContext(
        ClientRuntime runtime,
        String rawInput,
        String label,
        List<String> args,
        Consumer<String> feedback
) {
    public void reply(String message) {
        feedback.accept(message);
    }
}
