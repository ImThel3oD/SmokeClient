package com.smoke.client.command.impl;

import com.smoke.client.command.Command;
import com.smoke.client.command.CommandContext;
import com.smoke.client.module.Module;
import com.smoke.client.trace.ModuleTraceService;
import com.smoke.client.trace.TraceFocusScreen;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TraceCommand implements Command {
    @Override
    public String name() {
        return "trace";
    }

    @Override
    public List<String> aliases() {
        return List.of("story");
    }

    @Override
    public String description() {
        return "Shows, focuses, clears, and copies the module trace timeline.";
    }

    @Override
    public void execute(CommandContext context) {
        if (context.args().isEmpty()) {
            replyStatus(context);
            return;
        }

        ModuleTraceService trace = context.runtime().moduleTraceService();
        String action = normalize(context.args().get(0));
        switch (action) {
            case "status" -> replyStatus(context);
            case "auto" -> {
                trace.focusAuto();
                context.reply("Trace focus -> auto (" + trace.describeScope(null) + ")");
            }
            case "system" -> {
                trace.focusSystem();
                context.reply("Trace focus -> system");
            }
            case "focus" -> focusModule(context, trace);
            case "copy" -> copyTrace(context, trace);
            case "clear" -> clearTrace(context, trace);
            case "hud" -> setHud(context, trace);
            case "show" -> {
                trace.setHudEnabled(true);
                context.reply("Trace HUD -> shown");
            }
            case "hide" -> {
                trace.setHudEnabled(false);
                context.reply("Trace HUD -> hidden");
            }
            default -> replyUsage(context);
        }
    }

    private void replyStatus(CommandContext context) {
        ModuleTraceService trace = context.runtime().moduleTraceService();
        String scopeId = trace.resolvedScopeId();
        String label = trace.describeScope(scopeId);
        int entries = trace.entryCount(scopeId);
        context.reply("Trace HUD -> " + (trace.hudEnabled() ? "shown" : "hidden"));
        context.reply("Trace focus -> " + (trace.autoFocus() ? "auto" : "locked") + " (" + label + ")");
        context.reply("Trace entries -> " + entries);
        replyUsage(context);
    }

    private void focusModule(CommandContext context, ModuleTraceService trace) {
        if (context.args().size() < 2) {
            openFocusScreen(context);
            return;
        }

        List<String> targets = context.args().subList(1, context.args().size());
        if (targets.size() == 1) {
            String target = normalize(targets.get(0));
            if ("auto".equals(target) || "current".equals(target)) {
                trace.focusAuto();
                context.reply("Trace focus -> auto (" + trace.describeScope(null) + ")");
                return;
            }
            if (ModuleTraceService.SYSTEM_ID.equals(target)) {
                trace.focusSystem();
                context.reply("Trace focus -> system");
                return;
            }
            if (ModuleTraceService.ALL_ID.equals(target)) {
                trace.focusModules(List.of(ModuleTraceService.ALL_ID));
                context.reply("Trace focus -> all");
                return;
            }
        }

        List<String> focusIds = new ArrayList<>();
        for (String token : targets) {
            Module module = resolveModule(context, token);
            if (module == null) {
                context.reply("Unknown module: " + token);
                return;
            }
            focusIds.add(module.id());
        }

        trace.focusModules(focusIds);
        context.reply("Trace focus -> " + trace.describeScope(null));
    }

    private void openFocusScreen(CommandContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            context.reply("Trace focus picker unavailable.");
            return;
        }

        client.setScreen(new TraceFocusScreen(context.runtime(), null));
    }

    private void copyTrace(CommandContext context, ModuleTraceService trace) {
        String scopeId = scopeArgument(context.args(), 1);
        boolean copied;
        if (ModuleTraceService.ALL_ID.equals(scopeId)) {
            String export = trace.export(ModuleTraceService.ALL_ID);
            if (export.isBlank()) {
                context.reply("Trace copy failed: no entries.");
                return;
            }
            copied = trace.copyToClipboard(ModuleTraceService.ALL_ID);
        } else {
            copied = trace.copyToClipboard(scopeId);
        }

        if (!copied) {
            context.reply("Trace copy failed: no entries.");
            return;
        }

        String label = trace.describeScope(scopeId);
        int entries = trace.entryCount(scopeId);
        context.reply("Trace copied -> " + label + " (" + entries + " entries)");
    }

    private void clearTrace(CommandContext context, ModuleTraceService trace) {
        String scopeId = scopeArgument(context.args(), 1);
        if (ModuleTraceService.ALL_ID.equals(scopeId)) {
            trace.clearAll();
            context.reply("Trace cleared -> all");
            return;
        }

        String resolved = scopeId == null ? trace.resolvedScopeId() : scopeId;
        String label = trace.describeScope(resolved);
        trace.clearScope(resolved);
        context.reply("Trace cleared -> " + label);
    }

    private void setHud(CommandContext context, ModuleTraceService trace) {
        if (context.args().size() < 2) {
            context.reply("Usage: .trace hud <on|off>");
            return;
        }

        String value = normalize(context.args().get(1));
        if ("on".equals(value) || "show".equals(value)) {
            trace.setHudEnabled(true);
            context.reply("Trace HUD -> shown");
            return;
        }
        if ("off".equals(value) || "hide".equals(value)) {
            trace.setHudEnabled(false);
            context.reply("Trace HUD -> hidden");
            return;
        }

        context.reply("Usage: .trace hud <on|off>");
    }

    private void replyUsage(CommandContext context) {
        context.reply("Usage: .trace status | auto | system | focus [module...] | copy [current|module|system|all] | clear [current|module|system|all] | hud <on|off>");
    }

    private static Module resolveModule(CommandContext context, String token) {
        return context.runtime().moduleManager().getById(token)
                .or(() -> context.runtime().moduleManager().getByName(token))
                .orElse(null);
    }

    private static String scopeArgument(List<String> args, int index) {
        if (args.size() <= index) {
            return null;
        }

        String normalized = normalize(args.get(index));
        if ("auto".equals(normalized) || "current".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
