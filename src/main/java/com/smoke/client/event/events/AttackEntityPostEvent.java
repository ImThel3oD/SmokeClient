package com.smoke.client.event.events;

import com.smoke.client.event.Event;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Objects;

public final class AttackEntityPostEvent implements Event {
    private final PlayerEntity player;
    private final Entity target;

    public AttackEntityPostEvent(PlayerEntity player, Entity target) {
        this.player = Objects.requireNonNull(player, "player");
        this.target = Objects.requireNonNull(target, "target");
    }

    public PlayerEntity player() {
        return player;
    }

    public Entity target() {
        return target;
    }
}
