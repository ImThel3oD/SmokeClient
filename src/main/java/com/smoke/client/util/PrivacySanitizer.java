package com.smoke.client.util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class PrivacySanitizer {
    private static final Pattern FILE_URI_WINDOWS_PATH = Pattern.compile("(?i)file:/+[a-z]:[\\\\/][^\\s\"'<>|)\\]}]*");
    private static final Pattern FILE_URI_UNIX_PATH = Pattern.compile("(?i)file:/+/(?:Users|home)/[^\\s\"'<>|)\\]}]*");
    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("(?i)[a-z]:[\\\\/][^\\s\"'<>|)\\]}]*");
    private static final Pattern WINDOWS_UNC_PATH = Pattern.compile("\\\\\\\\[^\\\\\\s\"'<>|)\\]}]+\\\\[^\\s\"'<>|)\\]}]*");
    private static final Pattern UNIX_HOME_PATH = Pattern.compile("/(?:Users|home)/[^\\s\"'<>|)\\]}]*");

    private PrivacySanitizer() {
    }

    public static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String sanitized = text;
        for (PathAlias alias : knownAliases()) {
            sanitized = replaceIgnoreCase(sanitized, alias.value(), alias.label());
        }

        sanitized = FILE_URI_WINDOWS_PATH.matcher(sanitized).replaceAll("<path>");
        sanitized = FILE_URI_UNIX_PATH.matcher(sanitized).replaceAll("<path>");
        sanitized = WINDOWS_UNC_PATH.matcher(sanitized).replaceAll("<path>");
        sanitized = WINDOWS_ABSOLUTE_PATH.matcher(sanitized).replaceAll("<path>");
        sanitized = UNIX_HOME_PATH.matcher(sanitized).replaceAll("<path>");
        return sanitized;
    }

    public static String sanitizeThrowableMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return sanitize(message.trim());
    }

    private static List<PathAlias> knownAliases() {
        List<PathAlias> aliases = new ArrayList<>(4);
        addAlias(aliases, System.getProperty("user.home"), "<home>");
        addAlias(aliases, System.getProperty("user.dir"), "<project>");
        return aliases;
    }

    private static void addAlias(List<PathAlias> aliases, String rawPath, String label) {
        if (rawPath == null || rawPath.isBlank()) {
            return;
        }

        String normalized = rawPath.trim();
        aliases.add(new PathAlias(normalized, label));
        aliases.add(new PathAlias(normalized.replace('\\', '/'), label));
        aliases.add(new PathAlias(normalized.replace('/', '\\'), label));
    }

    private static String replaceIgnoreCase(String input, String target, String replacement) {
        if (target == null || target.isBlank()) {
            return input;
        }
        return Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE)
                .matcher(input)
                .replaceAll(replacement);
    }

    private record PathAlias(String value, String label) {
    }
}
