package com.smoke.client.event.events;

import com.smoke.client.event.Event;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Objects;

public final class AttackBlockEvent implements Event {
    private final BlockPos pos;
    private final Direction direction;

    public AttackBlockEvent(BlockPos pos, Direction direction) {
        this.pos = Objects.requireNonNull(pos, "pos");
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    public BlockPos pos() {
        return pos;
    }

    public Direction direction() {
        return direction;
    }
}

