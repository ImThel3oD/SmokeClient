package com.smoke.client.feature.module.combat;

import com.smoke.client.event.EventPriority;
import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.AttackEntityPreEvent;
import com.smoke.client.event.events.MovementInputEvent;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.module.AttackGate;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.rotation.RotationFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

public final class CriticalsModule extends Module implements AttackGate {
    private static final int TIMEOUT = 10;
    private Entity target;
    private int ticks;
    private boolean requestJump;

    public CriticalsModule(ModuleContext context) {
        super(context, "criticals", "Criticals", "Delays grounded attacks until the player is falling so vanilla critical hits land.", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    public boolean delay(Entity target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (this.target != null) return true;
        if (client.player == null || client.interactionManager == null || !canQueue(client, target)) return false;
        this.target = target;
        ticks = TIMEOUT;
        requestJump = true;
        return true;
    }

    @Override
    public boolean shouldBlockAttack(Entity target) {
        return delay(target);
    }

    @Subscribe(priority = EventPriority.HIGHEST)
    private void onAttackEntityPre(AttackEntityPreEvent event) {
        if (delay(event.target())) {
            event.cancel();
            event.stopPropagation();
        }
    }

    @Subscribe
    private void onMovementInput(MovementInputEvent event) {
        if (!requestJump) return;
        PlayerInput input = event.playerInput();
        event.setPlayerInput(new PlayerInput(input.forward(), input.backward(), input.left(), input.right(), true, input.sneak(), false));
        requestJump = false;
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (event.phase() != TickEvent.Phase.POST || target == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null || !validTarget(target) || !validState(client)) { reset(); return; }
        if (ready(client) && ((client.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() == target) || silentHit(client, target))) {
            Entity queued = target;
            reset();
            client.interactionManager.attackEntity(client.player, queued);
            client.player.swingHand(Hand.MAIN_HAND);
            return;
        }
        if (--ticks <= 0) reset();
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        target = null;
        ticks = 0;
        requestJump = false;
    }

    private boolean canQueue(MinecraftClient client, Entity target) {
        return client.player.isOnGround() && validTarget(target) && validState(client);
    }

    private boolean validTarget(Entity target) {
        return target instanceof LivingEntity && target.isAlive() && !target.isRemoved();
    }

    private boolean validState(MinecraftClient client) {
        return !client.player.isTouchingWater() && !client.player.isClimbing() && !client.player.hasStatusEffect(StatusEffects.BLINDNESS) && !client.player.hasVehicle() && !client.player.isSprinting();
    }

    private boolean ready(MinecraftClient client) {
        return !client.player.isOnGround() && client.player.getVelocity().y < 0.0 && client.player.fallDistance > 0.0;
    }

    private boolean silentHit(MinecraftClient client, Entity target) {
        RotationFrame frame = context().rotation().currentFrame();
        if (!frame.active() || client.world == null) return false;
        Vec3d start = client.player.getEyePos(), end = start.add(client.player.getRotationVector(frame.packetPitch(), frame.packetYaw()).multiply(client.player.getEntityInteractionRange()));
        BlockHitResult block = client.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player));
        Box box = target.getBoundingBox().expand(ProjectileUtil.getToleranceMargin(target));
        EntityHitResult hit = ProjectileUtil.getEntityCollision(client.world, client.player, start, end, box, entity -> entity == target, ProjectileUtil.DEFAULT_MARGIN);
        return hit != null && (block.getType() == HitResult.Type.MISS || start.squaredDistanceTo(hit.getPos()) <= start.squaredDistanceTo(block.getPos()) + 1.0E-4D);
    }
}
