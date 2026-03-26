package com.smoke.client.event.events;

import com.smoke.client.event.Event;
import net.minecraft.entity.Entity;

import java.util.Objects;

public final class EntityOutlineColorEvent implements Event {
    private final Entity entity;
    private int outlineColor = -1;

    public EntityOutlineColorEvent(Entity entity) {
        this.entity = Objects.requireNonNull(entity, "entity");
    }

    public Entity entity() {
        return entity;
    }

    public int outlineColor() {
        return outlineColor;
    }

    public void setOutlineColor(int outlineColor) {
        this.outlineColor = outlineColor;
    }
}
