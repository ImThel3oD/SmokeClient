package com.smoke.client.mixin.player;

import com.smoke.client.SmokeClient;
import com.smoke.client.bootstrap.ClientRuntime;
import com.smoke.client.event.events.AttackEntityPostEvent;
import com.smoke.client.event.events.AttackEntityPreEvent;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerAttackEntityMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void smoke$attackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        ClientRuntime runtime = SmokeClient.getRuntime();
        if (runtime == null) {
            return;
        }

        AttackEntityPreEvent event = runtime.eventBus().post(new AttackEntityPreEvent(player, target));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "attackEntity", at = @At("TAIL"))
    private void smoke$attackEntityPost(PlayerEntity player, Entity target, CallbackInfo ci) {
        ClientRuntime runtime = SmokeClient.getRuntime();
        if (runtime != null) {
            runtime.eventBus().post(new AttackEntityPostEvent(player, target));
        }
    }
}
