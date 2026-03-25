package com.smoke.client.mixin.render;

import com.smoke.client.feature.module.render.NameTagsModule;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
    private void smoke$hasLabel(Entity entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir) {
        if (NameTagsModule.shouldSuppressLabel(entity)) {
            cir.setReturnValue(false);
        }
    }
}
