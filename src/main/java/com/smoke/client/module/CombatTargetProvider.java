package com.smoke.client.module;

import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

public interface CombatTargetProvider {
    @Nullable Entity currentCombatTarget();
}
