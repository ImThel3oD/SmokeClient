package com.smoke.client.mixin.player;

import com.smoke.client.SmokeClient;
import com.smoke.client.bootstrap.ClientRuntime;
import com.smoke.client.event.events.AttackBlockEvent;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerAttackBlockMixin {
    @Shadow
    private void syncSelectedSlot() {
        throw new AssertionError("shadow");
    }

    @Inject(method = "attackBlock", at = @At("HEAD"))
    private void smoke$attackBlockPre(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        ClientRuntime runtime = SmokeClient.getRuntime();
        if (runtime != null) {
            runtime.eventBus().post(new AttackBlockEvent(pos, direction));
        }
        this.syncSelectedSlot();
    }
}
