package com.smoke.client.event.events;

import com.smoke.client.event.CancellableEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Objects;

public final class AttackEntityPreEvent extends CancellableEvent {
    private final PlayerEntity player;
    private final Entity target;

    public AttackEntityPreEvent(PlayerEntity player, Entity target) {
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
