package com.smoke.client.module;

import net.minecraft.entity.Entity;

public interface AttackGate {
    boolean shouldBlockAttack(Entity target);
}
