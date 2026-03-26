package com.smoke.client.mixin.player;

import com.smoke.client.SmokeClient;
import com.smoke.client.event.events.ClipAtLedgeEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "clipAtLedge", at = @At("HEAD"), cancellable = true)
    private void smoke$clipAtLedge(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this != MinecraftClient.getInstance().player || SmokeClient.getRuntime() == null) return;
        ClipAtLedgeEvent event = SmokeClient.getRuntime().eventBus().post(new ClipAtLedgeEvent((ClientPlayerEntity) (Object) this));
        if (event.forceClip()) {
            cir.setReturnValue(true);
        }
    }
}
