package com.smoke.client.event.events;

import com.smoke.client.event.Event;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

public record WorldRenderEvent(WorldRenderContext context) implements Event {
}
