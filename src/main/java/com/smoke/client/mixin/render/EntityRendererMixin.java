package com.smoke.client.mixin.render;

import com.smoke.client.SmokeClient;
import com.smoke.client.event.events.EntityLabelVisibilityEvent;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "hasLabel", at = @At("RETURN"), cancellable = true)
    private void smoke$hasLabel(Entity entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir) {
        if (SmokeClient.getRuntime() == null) {
            return;
        }

        boolean visible = cir.getReturnValueZ();
        EntityLabelVisibilityEvent event = SmokeClient.getRuntime().eventBus().post(
                new EntityLabelVisibilityEvent(entity, visible)
        );
        if (event.visible() != visible) {
            cir.setReturnValue(event.visible());
        }
    }
}
