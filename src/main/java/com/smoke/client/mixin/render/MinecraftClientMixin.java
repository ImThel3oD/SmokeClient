package com.smoke.client.mixin.render;

import com.smoke.client.feature.module.render.EspModule;
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
        if (!EspModule.isSupportedEntityTargetType(entity)) {
            return;
        }
        if (!cir.getReturnValue() && EspModule.shouldRenderOutline(entity)) {
            cir.setReturnValue(true);
        }
    }
}
