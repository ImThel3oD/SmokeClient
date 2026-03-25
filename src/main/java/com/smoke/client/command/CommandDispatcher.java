package com.smoke.client.command;

import com.smoke.client.bootstrap.ClientRuntime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class CommandDispatcher {
    private final Map<String, Command> commandsByName = new LinkedHashMap<>();
    private final Map<String, Command> lookup = new LinkedHashMap<>();
    private char prefix = '.';

    public void register(Command command) {
        Objects.requireNonNull(command, "command");
        String key = normalize(command.name());
        if (commandsByName.containsKey(key)) {
            throw new IllegalArgumentException("Duplicate command: " + command.name());
        }
        commandsByName.put(key, command);
        rebuildLookup();
    }

    public boolean isCommand(String input) {
        return input != null && !input.isBlank() && input.trim().charAt(0) == prefix;
    }

    public void dispatch(ClientRuntime runtime, String input) {
        String trimmed = input.trim();
        String body = trimmed.substring(1).trim();
        if (body.isEmpty()) {
            runtime.sendChatFeedback("No command specified.");
            return;
        }

        List<String> tokens = tokenize(body);
        if (tokens.isEmpty()) {
            runtime.sendChatFeedback("No command specified.");
            return;
        }

        Command command = lookup.get(normalize(tokens.get(0)));
        if (command == null) {
            runtime.sendChatFeedback("Unknown command: " + tokens.get(0));
            return;
        }

        List<String> args = tokens.size() > 1 ? tokens.subList(1, tokens.size()) : List.of();
        command.execute(new CommandContext(runtime, input, tokens.get(0), args, runtime::sendChatFeedback));
    }

    public List<Command> commands() {
        return List.copyOf(commandsByName.values());
    }

    public char prefix() {
        return prefix;
    }

    private void rebuildLookup() {
        lookup.clear();
        for (Command command : commandsByName.values()) {
            index(command.name(), command);
            for (String alias : command.aliases()) {
                index(alias, command);
            }
        }
    }

    private void index(String name, Command command) {
        String key = normalize(name);
        Command existing = lookup.putIfAbsent(key, command);
        if (existing != null && existing != command) {
            throw new IllegalArgumentException("Conflicting command alias: " + name);
        }
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quoteChar = '\0';

        for (int i = 0; i < input.length(); i++) {
            char character = input.charAt(i);
            if (quoted) {
                if (character == quoteChar) {
                    quoted = false;
                } else {
                    current.append(character);
                }
                continue;
            }

            if (character == '"' || character == '\'') {
                quoted = true;
                quoteChar = character;
                continue;
            }

            if (Character.isWhitespace(character)) {
                flush(current, tokens);
                continue;
            }

            current.append(character);
        }
        flush(current, tokens);
        return tokens;
    }

    private static void flush(StringBuilder current, List<String> tokens) {
        if (current.length() == 0) {
            return;
        }
        tokens.add(current.toString());
        current.setLength(0);
    }
}
