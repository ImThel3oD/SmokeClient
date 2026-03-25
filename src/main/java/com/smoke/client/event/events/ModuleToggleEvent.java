package com.smoke.client.event.events;

import com.smoke.client.event.Event;
import com.smoke.client.module.Module;

public record ModuleToggleEvent(Module module, boolean enabled) implements Event {
}
