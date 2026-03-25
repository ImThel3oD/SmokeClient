package com.smoke.client.event.events;

import com.smoke.client.event.Event;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public record HudRenderEvent(DrawContext drawContext, RenderTickCounter tickCounter) implements Event {
}
