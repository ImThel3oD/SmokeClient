package com.smoke.client.mixin.render;

import com.smoke.client.feature.module.render.EspModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void smoke$getTeamColorValue(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || self == client.player) {
            return;
        }
        if (!EspModule.isSupportedEntityTargetType(self)) {
            return;
        }

        int color = EspModule.getEntityOutlineColor(self);
        if (color != -1) {
            cir.setReturnValue(color);
        }
    }
}
