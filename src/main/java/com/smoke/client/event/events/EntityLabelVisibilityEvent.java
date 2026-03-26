package com.smoke.client.event.events;

import com.smoke.client.event.Event;
import net.minecraft.entity.Entity;

import java.util.Objects;

public final class EntityLabelVisibilityEvent implements Event {
    private final Entity entity;
    private boolean visible;

    public EntityLabelVisibilityEvent(Entity entity, boolean visible) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.visible = visible;
    }

    public Entity entity() {
        return entity;
    }

    public boolean visible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}
