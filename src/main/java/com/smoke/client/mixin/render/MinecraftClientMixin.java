package com.smoke.client.mixin.render;

import com.smoke.client.SmokeClient;
import com.smoke.client.event.events.EntityOutlineStateEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "hasOutline", at = @At("RETURN"), cancellable = true)
    private void smoke$hasOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SmokeClient.getRuntime() == null) {
            return;
        }

        boolean outlined = cir.getReturnValueZ();
        EntityOutlineStateEvent event = SmokeClient.getRuntime().eventBus().post(
                new EntityOutlineStateEvent(entity, outlined)
        );
        if (event.outlined() != outlined) {
            cir.setReturnValue(event.outlined());
        }
    }
}
