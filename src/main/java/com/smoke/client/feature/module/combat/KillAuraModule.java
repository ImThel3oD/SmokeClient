package com.smoke.client.feature.module.combat;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.mixin.accessor.MinecraftClientAccessor;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.rotation.RotationFrame;
import com.smoke.client.rotation.RotationMode;
import com.smoke.client.rotation.RotationRequest;
import com.smoke.client.setting.BoolSetting;
import com.smoke.client.setting.EnumSetting;
import com.smoke.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

public final class KillAuraModule extends Module {
    private static final int ROTATION_PRIORITY = 220;

    private final NumberSetting range = addSetting(new NumberSetting("range", "Range", "Maximum target distance.", 3.0D, 1.0D, 6.0D, 0.1D));
    private final NumberSetting fov = addSetting(new NumberSetting("fov", "FOV", "Target acquisition cone in degrees.", 180.0D, 1.0D, 360.0D, 1.0D));
    private final NumberSetting minCps = addSetting(new NumberSetting("min_cps", "CPS Min", "Minimum clicks per second.", 9.0D, 7.0D, 20.0D, 1.0D));
    private final NumberSetting maxCps = addSetting(new NumberSetting("max_cps", "CPS Max", "Maximum clicks per second.", 13.0D, 7.0D, 20.0D, 1.0D));
    private final EnumSetting<RotationSetting> rotation = addSetting(new EnumSetting<>("rotation", "Rotation", "How the server rotation is produced.", RotationSetting.class, RotationSetting.SILENT));
    private final EnumSetting<RaytraceSetting> raytrace = addSetting(new EnumSetting<>("raytrace", "Raytrace", "Attack validation method.", RaytraceSetting.class, RaytraceSetting.STRICT));
    private final BoolSetting players = addSetting(new BoolSetting("players", "Players", "Target players.", true));
    private final BoolSetting mobs = addSetting(new BoolSetting("mobs", "Mobs", "Target hostile mobs.", true));
    private final BoolSetting animals = addSetting(new BoolSetting("animals", "Animals", "Target passive animals.", false));

    private Entity plannedTarget;
    private double remainingDelayTicks;

    public KillAuraModule(ModuleContext ctx) {
        super(ctx, "kill_aura", "Kill Aura", "Automatically attacks the best valid target within range and FOV.", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    protected void onDisable() {
        reset();
    }

    @Subscribe
    private void onTick(TickEvent event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (event.phase() != TickEvent.Phase.PRE) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null || !mc.player.isAlive() || mc.currentScreen != null) { reset(); return; }
        if (remainingDelayTicks > 0.0D) remainingDelayTicks -= 1.0D;

        // PRE-only attack path: use the already-applied packet rotation/target from the previous tick,
        // then plan the next target so the next PRE has a valid server-facing frame to attack with.
        attackPlannedTarget(mc);
        plannedTarget = findBestTarget(mc);
        if (rotation.value() != RotationSetting.SILENT || plannedTarget == null) { context().rotation().release(id()); return; }

        float[] aim = aimAngles(mc.player.getEyePos(), aimPoint(mc.player.getEyePos(), plannedTarget));
        context().rotation().submit(new RotationRequest(
                id(), aim[0], aim[1], ROTATION_PRIORITY, RotationRequest.TTL_PERSISTENT,
                180.0F, 180.0F, RotationMode.SILENT_STICKY, true, null, true, true, true
        ));
    }

    private void attackPlannedTarget(MinecraftClient mc) {
        if (plannedTarget == null || !isValidTarget(mc, plannedTarget)) return;
        if (remainingDelayTicks > 0.0D || mc.player.getAttackCooldownProgress(0.0F) < 1.0F) return;

        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        if (rotation.value() == RotationSetting.SILENT) {
            RotationFrame frame = context().rotation().currentFrame();
            if (!frame.active() || !id().equals(frame.ownerId())) return;
            yaw = frame.packetYaw();
            pitch = frame.packetPitch();
        }

        EntityHitResult hit = raytrace.value() == RaytraceSetting.STRICT
                ? exactHit(mc.player, plannedTarget, yaw, pitch)
                : syntheticHit(mc.player, plannedTarget);
        if (raytrace.value() == RaytraceSetting.STRICT && hit == null) return;

        CriticalsModule criticals = context().modules().getByType(CriticalsModule.class).orElse(null);
        if (criticals != null && criticals.enabled() && criticals.delay(plannedTarget)) return;

        attack(mc, hit);
        remainingDelayTicks = nextDelayTicks();
    }

    private Entity findBestTarget(MinecraftClient mc) {
        double maxSq = range.value() * range.value();
        double halfFov = fov.value() * 0.5D;
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0F);

        Entity best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        double bestAngle = Double.POSITIVE_INFINITY;
        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(mc, entity)) continue;
            double distSq = mc.player.squaredDistanceTo(entity);
            if (distSq > maxSq) continue;
            Vec3d to = entity.getEyePos().subtract(eye);
            double lenSq = to.lengthSquared();
            if (lenSq <= 1.0E-8D) continue;
            double angle = 0.0D;
            if (fov.value() < 360.0D) {
                angle = Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(to.multiply(1.0D / Math.sqrt(lenSq))), -1.0D, 1.0D)));
                if (angle > halfFov) continue;
            }
            if (distSq < bestDistSq || (distSq == bestDistSq && angle < bestAngle)) {
                best = entity;
                bestDistSq = distSq;
                bestAngle = angle;
            }
        }
        return best;
    }

    private boolean isValidTarget(MinecraftClient mc, Entity entity) {
        if (entity == null || entity == mc.player || entity.isRemoved() || !entity.isAlive()) return false;
        if (entity instanceof PlayerEntity player) return players.value() && !player.isSpectator() && !AntiBot.isBot(player);
        if (entity instanceof Monster) return mobs.value();
        return entity instanceof AnimalEntity && animals.value();
    }

    private EntityHitResult exactHit(ClientPlayerEntity player, Entity target, float yaw, float pitch) {
        Vec3d start = player.getEyePos();
        double blockRange = player.getBlockInteractionRange(), entityRange = player.getEntityInteractionRange();
        double traceRange = Math.max(blockRange, entityRange), blockSq = MathHelper.square(traceRange);
        Vec3d look = player.getRotationVector(pitch, yaw);
        HitResult block = player.getWorld().raycast(new RaycastContext(start, start.add(look.multiply(traceRange)), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        if (block.getType() != HitResult.Type.MISS) { blockSq = start.squaredDistanceTo(block.getPos()); traceRange = Math.sqrt(blockSq); }
        Vec3d end = start.add(look.multiply(traceRange));
        Box search = player.getBoundingBox().stretch(look.multiply(traceRange)).expand(1.0D, 1.0D, 1.0D);
        EntityHitResult hit = ProjectileUtil.raycast(player, start, end, search, entity -> entity == target && EntityPredicates.CAN_HIT.test(entity), blockSq);
        if (hit == null) return null;
        double hitSq = start.squaredDistanceTo(hit.getPos());
        return hitSq < blockSq && hitSq <= entityRange * entityRange ? hit : null;
    }

    private static EntityHitResult syntheticHit(ClientPlayerEntity player, Entity target) {
        return new EntityHitResult(target, aimPoint(player.getEyePos(), target));
    }

    private static void attack(MinecraftClient client, EntityHitResult hit) {
        HitResult prev = client.crosshairTarget;
        Entity prevTarget = client.targetedEntity;
        client.crosshairTarget = hit;
        client.targetedEntity = hit.getEntity();
        try {
            ((MinecraftClientAccessor) (Object) client).smoke$doAttack();
        } finally {
            client.crosshairTarget = prev;
            client.targetedEntity = prevTarget;
        }
    }

    private static Vec3d aimPoint(Vec3d from, Entity target) {
        Box box = target.getBoundingBox();
        return new Vec3d(
                MathHelper.clamp(from.x, box.minX, box.maxX),
                MathHelper.clamp(from.y, box.minY, box.maxY),
                MathHelper.clamp(from.z, box.minZ, box.maxZ)
        );
    }

    private static float[] aimAngles(Vec3d from, Vec3d to) {
        Vec3d d = to.subtract(from);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        return new float[]{ (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0D), (float) -Math.toDegrees(Math.atan2(d.y, h)) };
    }

    private double nextDelayTicks() {
        double min = minCps.value();
        double max = Math.max(min, maxCps.value());
        double cps = ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max));
        return 20.0D / cps;
    }

    private void reset() {
        plannedTarget = null;
        remainingDelayTicks = 0.0D;
        context().rotation().release(id());
    }

    private enum RotationSetting { SILENT, NONE }
    private enum RaytraceSetting { STRICT, NONE }
}
