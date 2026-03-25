package com.smoke.client.mixin.player;

import com.smoke.client.SmokeClient;
import com.smoke.client.event.events.MovementInputEvent;
import com.smoke.client.mixin.accessor.InputAccessor;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private void smoke$transformMovementInput(CallbackInfo ci) {
        if (SmokeClient.getRuntime() == null) {
            return;
        }

        InputAccessor accessor = (InputAccessor) this;
        MovementInputEvent event = SmokeClient.getRuntime().eventBus().post(
                new MovementInputEvent(accessor.smoke$getPlayerInput(), accessor.smoke$getMovementVector())
        );
        accessor.smoke$setPlayerInput(event.playerInput());
        accessor.smoke$setMovementVector(event.movementVector());
    }
}
