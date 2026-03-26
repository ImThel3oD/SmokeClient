package com.smoke.client.feature.module.combat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.event.events.WorldRenderEvent;
import com.smoke.client.mixin.accessor.MinecraftClientAccessor;
import com.smoke.client.module.CombatTargetProvider;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.rotation.RotationFrame;
import com.smoke.client.rotation.RotationMode;
import com.smoke.client.rotation.RotationRequest;
import com.smoke.client.setting.BoolSetting;
import com.smoke.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public final class SilentAuraModule extends Module implements CombatTargetProvider {
    private static final int ROTATION_PRIORITY = 220;
    private static final int CIRCLE_SEGMENTS = 64;
    private static final int GLOW_RINGS = 12;

    private final NumberSetting range = addSetting(new NumberSetting("range", "Range", "Maximum target distance.", 3.0D, 1.0D, 6.0D, 0.1D));
    private final NumberSetting fov = addSetting(new NumberSetting("fov", "FOV", "Target acquisition cone from camera direction.", 120.0D, 1.0D, 360.0D, 1.0D));
    private final BoolSetting renderTarget = addSetting(new BoolSetting("render_target", "Render Target", "Draw a circle indicator on the selected target.", true));
    private final BoolSetting players = addSetting(new BoolSetting("players", "Players", "Target players.", true));
    private final BoolSetting mobs = addSetting(new BoolSetting("mobs", "Mobs", "Target hostile mobs.", false));
    private final BoolSetting animals = addSetting(new BoolSetting("animals", "Animals", "Target passive animals.", false));

    private final BufferAllocator allocator = new BufferAllocator(RenderLayer.getLines().getExpectedBufferSize());
    private final VertexConsumerProvider.Immediate consumers = VertexConsumerProvider.immediate(allocator);

    private Entity currentTarget;
    private Entity pendingTarget;

    public SilentAuraModule(ModuleContext ctx) {
        super(ctx, "silent_aura", "Silent Aura",
                "Attacks the best valid target when you left-click, using a one-tick silent rotation flick.",
                ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    public Entity getTarget() {
        return currentTarget;
    }

    @Override
    public Entity currentCombatTarget() {
        return currentTarget;
    }

    @Override
    protected void onDisable() {
        reset();
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (event.phase() != TickEvent.Phase.PRE) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null
                || !mc.player.isAlive() || mc.currentScreen != null) {
            reset();
            return;
        }

        if (pendingTarget != null) {
            RotationFrame frame = context().rotation().currentFrame();
            if (frame.active() && id().equals(frame.ownerId()) && isValidTarget(mc, pendingTarget)) {
                EntityHitResult hit = exactHit(mc.player, pendingTarget, frame.packetYaw(), frame.packetPitch());
                if (hit != null) attack(mc, hit);
            }
            context().rotation().release(id());
            pendingTarget = null;
        }

        currentTarget = findBestTarget(mc);
        if (currentTarget == null) return;

        while (mc.options.attackKey.wasPressed()) {
            Vec3d eye = mc.player.getEyePos();
            float[] aim = aimAngles(eye, aimPoint(eye, currentTarget));
            EntityHitResult preHit = exactHit(mc.player, currentTarget, aim[0], aim[1]);
            if (preHit != null) {
                context().rotation().submit(new RotationRequest(
                        id(), aim[0], aim[1], ROTATION_PRIORITY, 1,
                        180.0F, 180.0F, RotationMode.SILENT, true
                ));
                pendingTarget = currentTarget;
            } else {
                ((MinecraftClientAccessor) (Object) mc).smoke$doAttack();
            }
            break;
        }
        while (mc.options.attackKey.wasPressed()) { /* drain extra presses */ }
    }

    @Subscribe
    private void onWorldRender(WorldRenderEvent event) {
        if (!renderTarget.value() || currentTarget == null || !currentTarget.isAlive() || currentTarget.isRemoved()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || event.context().camera() == null) return;

        float tickDelta = event.context().tickCounter().getTickProgress(true);
        Vec3d camera = event.context().camera().getPos();
        double tx = MathHelper.lerp(tickDelta, currentTarget.lastRenderX, currentTarget.getX());
        double ty = MathHelper.lerp(tickDelta, currentTarget.lastRenderY, currentTarget.getY());
        double tz = MathHelper.lerp(tickDelta, currentTarget.lastRenderZ, currentTarget.getZ());
        float baseRadius = currentTarget.getWidth() * 0.7F;
        float pulse = 0.6F + 0.4F * (float) Math.sin(System.currentTimeMillis() * 0.004D);

        MatrixStack matrices = new MatrixStack();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        matrices.translate(tx, ty + 0.02D, tz);
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        Matrix4f posMatrix = matrices.peek().getPositionMatrix();

        RenderSystem.lineWidth(2.5F);
        try {
            for (int ring = 0; ring < GLOW_RINGS; ring++) {
                float t = (float) ring / (GLOW_RINGS - 1);
                drawCircle(posMatrix, lines, baseRadius + t * baseRadius * 0.8F,
                        0.4F, 0.8F, 1.0F, pulse * (1.0F - t) * 0.35F);
            }
            RenderSystem.lineWidth(3.0F);
            drawCircle(posMatrix, lines, baseRadius, 0.5F, 0.9F, 1.0F, pulse * 0.9F);
            consumers.draw();
        } finally {
            RenderSystem.lineWidth(1.0F);
        }
    }

    private static void drawCircle(Matrix4f matrix, VertexConsumer consumer, float radius,
                                   float r, float g, float b, float a) {
        float prevX = radius, prevZ = 0.0F;
        for (int i = 1; i <= CIRCLE_SEGMENTS; i++) {
            float angle = (float) (i * 2.0D * Math.PI / CIRCLE_SEGMENTS);
            float cx = MathHelper.cos(angle) * radius;
            float cz = MathHelper.sin(angle) * radius;
            consumer.vertex(matrix, prevX, 0.0F, prevZ).color(r, g, b, a).normal(0.0F, 1.0F, 0.0F);
            consumer.vertex(matrix, cx, 0.0F, cz).color(r, g, b, a).normal(0.0F, 1.0F, 0.0F);
            prevX = cx;
            prevZ = cz;
        }
    }

    private Entity findBestTarget(MinecraftClient mc) {
        double maxSq = range.value() * range.value();
        double halfFov = fov.value() * 0.5D;
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0F);
        Entity best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(mc, entity)) continue;
            double distSq = mc.player.squaredDistanceTo(entity);
            if (distSq > maxSq || distSq >= bestDistSq) continue;
            if (fov.value() < 360.0D) {
                Vec3d to = entity.getEyePos().subtract(eye);
                double lenSq = to.lengthSquared();
                if (lenSq <= 1.0E-8D) continue;
                double angle = Math.toDegrees(Math.acos(MathHelper.clamp(
                        look.dotProduct(to.multiply(1.0D / Math.sqrt(lenSq))), -1.0D, 1.0D)));
                if (angle > halfFov) continue;
            }
            best = entity;
            bestDistSq = distSq;
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
        double entityRange = player.getEntityInteractionRange();
        double traceRange = Math.max(player.getBlockInteractionRange(), entityRange);
        double blockSq = MathHelper.square(traceRange);
        Vec3d look = player.getRotationVector(pitch, yaw);
        HitResult block = player.getWorld().raycast(new RaycastContext(
                start, start.add(look.multiply(traceRange)),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            blockSq = start.squaredDistanceTo(block.getPos());
            traceRange = Math.sqrt(blockSq);
        }
        Vec3d end = start.add(look.multiply(traceRange));
        Box search = player.getBoundingBox().stretch(look.multiply(traceRange)).expand(1.0D, 1.0D, 1.0D);
        EntityHitResult hit = ProjectileUtil.raycast(player, start, end, search,
                e -> e == target && EntityPredicates.CAN_HIT.test(e), blockSq);
        if (hit == null) return null;
        double hitSq = start.squaredDistanceTo(hit.getPos());
        return hitSq < blockSq && hitSq <= entityRange * entityRange ? hit : null;
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
                MathHelper.clamp(from.z, box.minZ, box.maxZ));
    }

    private static float[] aimAngles(Vec3d from, Vec3d to) {
        Vec3d d = to.subtract(from);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        return new float[]{
                (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0D),
                (float) -Math.toDegrees(Math.atan2(d.y, h))
        };
    }

    private void reset() {
        currentTarget = null;
        pendingTarget = null;
        context().rotation().release(id());
    }
}
