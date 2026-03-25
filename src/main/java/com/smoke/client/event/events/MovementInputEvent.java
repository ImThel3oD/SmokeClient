package com.smoke.client.event.events;

import com.smoke.client.event.Event;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;

import java.util.Objects;

public final class MovementInputEvent implements Event {
    private PlayerInput playerInput;
    private Vec2f movementVector;

    public MovementInputEvent(PlayerInput playerInput, Vec2f movementVector) {
        this.playerInput = Objects.requireNonNull(playerInput, "playerInput");
        this.movementVector = Objects.requireNonNull(movementVector, "movementVector");
    }

    public PlayerInput playerInput() {
        return playerInput;
    }

    public void setPlayerInput(PlayerInput playerInput) {
        this.playerInput = Objects.requireNonNull(playerInput, "playerInput");
    }

    public Vec2f movementVector() {
        return movementVector;
    }

    public void setMovementVector(Vec2f movementVector) {
        this.movementVector = Objects.requireNonNull(movementVector, "movementVector");
    }
}
