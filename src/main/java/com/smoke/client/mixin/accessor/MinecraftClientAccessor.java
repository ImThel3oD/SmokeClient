package com.smoke.client.mixin.accessor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("session")
    Session smoke$getSession();

    @Mutable
    @Accessor("session")
    void smoke$setSession(Session session);

    @Accessor("itemUseCooldown")
    int smoke$getItemUseCooldown();

    @Accessor("itemUseCooldown")
    void smoke$setItemUseCooldown(int itemUseCooldown);

    @Invoker("doAttack")
    boolean smoke$doAttack();

    @Invoker("doItemUse")
    void smoke$doItemUse();
}
