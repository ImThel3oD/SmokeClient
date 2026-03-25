package com.smoke.client.mixin.accessor;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor {
    @Accessor("value")
    Object smoke$getValue();

    @Accessor("value")
    void smoke$setValue(Object value);
}

