package com.smoke.client.mixin.accessor;

import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Input.class)
public interface InputAccessor {
    @Accessor("playerInput")
    PlayerInput smoke$getPlayerInput();

    @Accessor("playerInput")
    void smoke$setPlayerInput(PlayerInput playerInput);

    @Accessor("movementVector")
    Vec2f smoke$getMovementVector();

    @Accessor("movementVector")
    void smoke$setMovementVector(Vec2f movementVector);
}
