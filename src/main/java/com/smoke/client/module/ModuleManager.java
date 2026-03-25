package com.smoke.client.module;

import com.smoke.client.event.EventBus;
import com.smoke.client.event.events.ModuleToggleEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ModuleManager {
    private final EventBus eventBus;
    private final List<Module> modules = new ArrayList<>();
    private final Map<String, Module> modulesById = new LinkedHashMap<>();

    public ModuleManager(EventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    public void register(Module module) {
        Objects.requireNonNull(module, "module");
        if (modulesById.containsKey(module.id())) {
            throw new IllegalArgumentException("Duplicate module id: " + module.id());
        }
        modules.add(module);
        modulesById.put(module.id(), module);
        if (module.enabled()) {
            module.enableInternal();
            eventBus.register(module);
        }
    }

    public Optional<Module> getById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(modulesById.get(id.trim().toLowerCase()));
    }

    public Optional<Module> getByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        return modules.stream()
                .filter(module -> module.name().equalsIgnoreCase(name))
                .findFirst();
    }

    public <T extends Module> Optional<T> getByType(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return modules.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst();
    }

    public List<Module> all() {
        return List.copyOf(modules);
    }

    public List<Module> byCategory(ModuleCategory category) {
        return modules.stream()
                .filter(module -> module.category() == category)
                .sorted(Comparator.comparing(Module::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<Module> enabledModules() {
        return modules.stream()
                .filter(Module::enabled)
                .toList();
    }

    public void toggle(Module module) {
        setEnabled(module, !module.enabled());
    }

    public void setEnabled(Module module, boolean enabled) {
        Objects.requireNonNull(module, "module");
        if (module.enabled() == enabled) {
            return;
        }

        module.setEnabled(enabled);
        if (enabled) {
            module.enableInternal();
            eventBus.register(module);
        } else {
            eventBus.unregister(module);
            module.disableInternal();
        }
        eventBus.post(new ModuleToggleEvent(module, enabled));
    }

    public void shutdown() {
        for (Module module : modules) {
            if (module.enabled()) {
                setEnabled(module, false);
            } else {
                eventBus.unregister(module);
            }
        }
    }
}
