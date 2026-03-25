package com.smoke.client.mixin.accessor;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("lastBodyYaw")
    void smoke$setLastBodyYaw(float lastBodyYaw);

    @Accessor("lastHeadYaw")
    void smoke$setLastHeadYaw(float lastHeadYaw);
}
