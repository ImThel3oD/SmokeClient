package com.smoke.client.event.events;

import com.smoke.client.event.Event;
import net.minecraft.entity.Entity;

import java.util.Objects;

public final class EntityOutlineStateEvent implements Event {
    private final Entity entity;
    private boolean outlined;

    public EntityOutlineStateEvent(Entity entity, boolean outlined) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.outlined = outlined;
    }

    public Entity entity() {
        return entity;
    }

    public boolean outlined() {
        return outlined;
    }

    public void setOutlined(boolean outlined) {
        this.outlined = outlined;
    }
}
