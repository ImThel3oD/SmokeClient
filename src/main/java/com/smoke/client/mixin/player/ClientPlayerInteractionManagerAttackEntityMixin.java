package com.smoke.client.mixin.player;

import com.smoke.client.SmokeClient;
import com.smoke.client.bootstrap.ClientRuntime;
import com.smoke.client.feature.module.combat.BacktrackModule;
import com.smoke.client.feature.module.combat.CriticalsModule;
import com.smoke.client.feature.module.combat.KeepSprintModule;
import com.smoke.client.feature.module.combat.KnockbackDelayModule;
import com.smoke.client.feature.module.combat.WTapModule;
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
        CriticalsModule criticals = runtime == null ? null : runtime.moduleManager().getByType(CriticalsModule.class).orElse(null);
        if (criticals != null && criticals.enabled() && criticals.delay(target)) { ci.cancel(); return; }
        WTapModule wTap = runtime == null ? null : runtime.moduleManager().getByType(WTapModule.class).orElse(null);
        if (wTap != null && wTap.enabled()) wTap.trigger(player, target);
        KeepSprintModule keepSprint = runtime == null ? null : runtime.moduleManager().getByType(KeepSprintModule.class).orElse(null);
        if (keepSprint != null && keepSprint.enabled()) keepSprint.beforeAttack(player);
    }

    @Inject(method = "attackEntity", at = @At("TAIL"))
    private void smoke$attackEntityPost(PlayerEntity player, Entity target, CallbackInfo ci) {
        ClientRuntime runtime = SmokeClient.getRuntime();
        KeepSprintModule keepSprint = runtime == null ? null : runtime.moduleManager().getByType(KeepSprintModule.class).orElse(null);
        if (keepSprint != null && keepSprint.enabled()) keepSprint.afterAttack(player);
        BacktrackModule backtrack = runtime == null ? null : runtime.moduleManager().getByType(BacktrackModule.class).orElse(null);
        if (backtrack != null && backtrack.enabled()) backtrack.recordAttackTarget(target);
        KnockbackDelayModule module = runtime == null ? null : runtime.moduleManager().getByType(KnockbackDelayModule.class).orElse(null);
        if (module != null && module.enabled()) module.trigger(target);
    }
}
