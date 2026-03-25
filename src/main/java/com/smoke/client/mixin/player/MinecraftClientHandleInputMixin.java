package com.smoke.client.mixin.player;

import com.smoke.client.SmokeClient;
import com.smoke.client.bootstrap.ClientRuntime;
import com.smoke.client.event.events.HandleInputEvent;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientHandleInputMixin {
    @Inject(
            method = "handleInputEvents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z",
                    ordinal = 0
            )
    )
    private void smoke$handleInputEventsPre(CallbackInfo ci) {
        ClientRuntime runtime = SmokeClient.getRuntime();
        if (runtime == null) {
            return;
        }
        runtime.eventBus().post(HandleInputEvent.pre());
    }

    @Inject(method = "handleInputEvents", at = @At("RETURN"))
    private void smoke$handleInputEventsPost(CallbackInfo ci) {
        ClientRuntime runtime = SmokeClient.getRuntime();
        if (runtime == null) {
            return;
        }
        runtime.eventBus().post(HandleInputEvent.post());
    }
}
