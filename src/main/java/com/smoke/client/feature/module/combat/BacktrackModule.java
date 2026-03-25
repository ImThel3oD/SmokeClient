package com.smoke.client.feature.module.combat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.smoke.client.event.EventPriority;
import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.PacketInboundEvent;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.event.events.WorldRenderEvent;
import com.smoke.client.mixin.accessor.EntityS2CPacketAccessor;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.BoolSetting;
import com.smoke.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class BacktrackModule extends Module {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final double DISTANCE_EPSILON_SQ = 0.0025D;
    private static final double DELTA_SCALE = 4096.0D;
    private static final double RENDER_EPSILON_SQ = 0.01D;
    private static final int MAX_BUFFERED_PACKETS = 1024;

    private final NumberSetting delay = addSetting(new NumberSetting("delay", "Delay", "Milliseconds to delay incoming play packets.", 200.0D, 0.0D, 1000.0D, 25.0D));
    private final BoolSetting render = addSetting(new BoolSetting("render", "Render", "Draw a ghost box at the tracked server position.", true));
    private final ConcurrentLinkedDeque<BufferedInboundPacket> inboundQueue = new ConcurrentLinkedDeque<>();
    private final Map<Integer, TrackedTargetState> trackedTargets = new ConcurrentHashMap<>();
    private final ThreadLocal<Boolean> replaying = ThreadLocal.withInitial(() -> false);
    private final BufferAllocator allocator = new BufferAllocator(RenderLayer.getLines().getExpectedBufferSize());
    private final VertexConsumerProvider.Immediate consumers = VertexConsumerProvider.immediate(allocator);

    private volatile boolean laggingPackets;

    public BacktrackModule(ModuleContext context) {
        super(context, "backtrack", "Backtrack", "Delays incoming play packets for tracked targets that are moving away.", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        if (isPlaySessionActive()) {
            flushAll();
        } else {
            resetState();
        }
    }

    @Override
    public String displaySuffix() {
        int queued = inboundQueue.size();
        int delayMs = delay.value().intValue();
        return queued == 0 ? delayMs + "ms" : queued + " | " + delayMs + "ms";
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (event.phase() != TickEvent.Phase.PRE) {
            return;
        }

        if (!isPlaySessionActive()) {
            disableOutsidePlaySession();
            return;
        }

        if (!pruneTrackedTargets()) {
            laggingPackets = false;
            flushAll();
            return;
        }

        long delayMillis = delay.value().longValue();
        if (delayMillis <= 0L || !laggingPackets) {
            flushAll();
            return;
        }

        flushDuePackets();
    }

    @Subscribe(priority = EventPriority.HIGHEST)
    private void onPacket(PacketInboundEvent event) {
        if (Boolean.TRUE.equals(replaying.get())) {
            return;
        }

        ClientConnection connection = currentConnection();
        if (!isPlaySession(connection)) {
            return;
        }

        Packet<?> packet = event.packet();
        if (shouldBypassDelay(packet)) {
            handleBypassedPacket(packet);
            return;
        }

        TrackedMovementUpdate trackedMovement = resolveTrackedMovement(packet);
        if (trackedMovement != null) {
            boolean shouldLag = shouldLag(trackedMovement.targetState(), trackedMovement.movement().projectedPos());
            trackedMovement.targetState().lagging = shouldLag;
            recomputeLaggingState();
            if (!shouldLag) {
                flushIfNoLaggingTargets();
            }
        }

        long delayMillis = delay.value().longValue();
        if (delayMillis <= 0L || !laggingPackets) {
            return;
        }

        long now = System.currentTimeMillis();
        if (trackedMovement != null) {
            trackedMovement.targetState().queuedMovements.addLast(trackedMovement.movement());
        }
        inboundQueue.addLast(new BufferedInboundPacket(
                connection,
                packet,
                trackedMovement == null ? null : trackedMovement.targetState(),
                trackedMovement == null ? null : trackedMovement.movement(),
                now + delayMillis
        ));
        enforceBufferLimit(now);
        event.cancel();
        event.stopPropagation();
    }

    @Subscribe
    private void onRender(WorldRenderEvent event) {
        if (!render.value() || MC.player == null || MC.world == null || event.context().camera() == null) {
            return;
        }

        List<RenderedGhost> ghosts = collectRenderedGhosts();
        if (ghosts.isEmpty()) {
            return;
        }

        MatrixStack matrices = new MatrixStack();
        Vec3d camera = event.context().camera().getPos();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        RenderSystem.lineWidth(2.0F);
        try {
            for (RenderedGhost ghost : ghosts) {
                Box box = ghost.box();
                VertexRendering.drawBox(matrices, lines, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 1.0F, 0.37F, 0.27F, 0.82F);
            }
            consumers.draw();
        } finally {
            RenderSystem.lineWidth(1.0F);
        }
    }

    public void trackTarget(@Nullable LivingEntity entity) {
        if (!enabled() || entity == null || entity.isRemoved() || !entity.isAlive()) {
            return;
        }

        upsertTrackedTarget(entity, System.currentTimeMillis());
    }

    public void setTrackedTarget(@Nullable LivingEntity entity) {
        trackTarget(entity);
    }

    public void clearTrackedTarget(@Nullable LivingEntity entity) {
        if (entity == null) {
            return;
        }

        TrackedTargetState state = trackedTargets.remove(entity.getId());
        if (state == null) {
            return;
        }

        state.queuedMovements.clear();
        state.lagging = false;
        flushIfNoLaggingTargets();
    }

    public void clearTrackedTargets() {
        if (isPlaySessionActive()) {
            flushAll();
        }
        resetState();
    }

    public boolean isTracking(@Nullable Entity entity) {
        if (entity == null) {
            return false;
        }

        TrackedTargetState state = trackedTargets.get(entity.getId());
        return state != null && (!state.queuedMovements.isEmpty() || state.lagging);
    }

    public @Nullable LivingEntity getTrackedTarget() {
        return resolvePrimaryTrackedTarget();
    }

    public boolean isBuffering() {
        return enabled() && !inboundQueue.isEmpty();
    }

    public int getBufferSize() {
        return inboundQueue.size();
    }

    public boolean isReplaying() {
        return Boolean.TRUE.equals(replaying.get());
    }

    public @Nullable Vec3d getRealPosition(@Nullable LivingEntity entity) {
        if (entity == null) {
            return null;
        }

        TrackedTargetState state = trackedTargets.get(entity.getId());
        if (state == null || state.entity != entity) {
            return null;
        }

        TrackedMovement newestMovement = state.queuedMovements.peekLast();
        return newestMovement == null ? null : newestMovement.projectedPos();
    }

    public void recordAttackTarget(@Nullable Entity targetEntity) {
        onAttackPacketSent(targetEntity);
    }

    public void onAttackPacketSent(@Nullable Entity targetEntity) {
        trackTarget(targetEntity instanceof LivingEntity livingTarget ? livingTarget : null);
    }

    private void flushDuePackets() {
        long now = System.currentTimeMillis();
        while (true) {
            BufferedInboundPacket bufferedPacket = inboundQueue.peekFirst();
            if (bufferedPacket == null || bufferedPacket.releaseAtMillis() > now) {
                return;
            }

            inboundQueue.pollFirst();
            replayPacket(bufferedPacket, now);
        }
    }

    private void flushAll() {
        long now = System.currentTimeMillis();
        while (true) {
            BufferedInboundPacket bufferedPacket = inboundQueue.pollFirst();
            if (bufferedPacket == null) {
                return;
            }

            replayPacket(bufferedPacket, now);
        }
    }

    private void replayPacket(@Nullable BufferedInboundPacket bufferedPacket, long now) {
        if (bufferedPacket == null) {
            return;
        }

        releaseTrackedMovement(bufferedPacket.targetState(), bufferedPacket.movement());

        ClientConnection activeConnection = currentConnection();
        ClientConnection bufferedConnection = bufferedPacket.connection();
        if (bufferedConnection == null || !bufferedConnection.isOpen() || bufferedConnection != activeConnection) {
            return;
        }

        PacketListener listener = bufferedConnection.getPacketListener();
        if (listener == null || !listener.isConnectionOpen()) {
            return;
        }

        replaying.set(true);
        try {
            var decision = context().packets().handleInbound(bufferedPacket.packet());
            if (decision.cancelled()) {
                return;
            }
            Packet<?> packet = decision.packet();
            if (!listener.accepts(packet)) {
                return;
            }
            applyPacket(packet, listener);
        } catch (Exception ignored) {
        } finally {
            replaying.set(false);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyPacket(Packet<?> packet, PacketListener listener) {
        ((Packet) packet).apply(listener);
    }

    private @Nullable ClientConnection currentConnection() {
        return MC.getNetworkHandler() == null ? null : MC.getNetworkHandler().getConnection();
    }

    private static boolean shouldBypassDelay(Packet<?> packet) {
        return packet instanceof KeepAliveS2CPacket
                || packet instanceof CommonPingS2CPacket
                || packet instanceof DisconnectS2CPacket
                || packet instanceof PlayerPositionLookS2CPacket
                || packet instanceof GameJoinS2CPacket
                || packet instanceof PlayerRespawnS2CPacket
                || packet instanceof HealthUpdateS2CPacket;
    }

    private void handleBypassedPacket(Packet<?> packet) {
        if (packet instanceof DisconnectS2CPacket
                || packet instanceof PlayerPositionLookS2CPacket
                || packet instanceof GameJoinS2CPacket
                || packet instanceof PlayerRespawnS2CPacket) {
            laggingPackets = false;
            flushAll();
        }
    }

    private void enforceBufferLimit(long now) {
        while (inboundQueue.size() > MAX_BUFFERED_PACKETS) {
            BufferedInboundPacket oldest = inboundQueue.pollFirst();
            if (oldest == null) {
                return;
            }

            replayPacket(oldest, now);
        }
    }

    private boolean pruneTrackedTargets() {
        long now = System.currentTimeMillis();
        long timeoutMillis = resolveTrackTimeoutMillis();

        for (TrackedTargetState state : new ArrayList<>(trackedTargets.values())) {
            LivingEntity target = state.entity;
            if (MC.player == null
                    || target == null
                    || !target.isAlive()
                    || target.isRemoved()
                    || target.getWorld() != MC.world
                    || now - state.lastTrackedAtMillis > timeoutMillis) {
                trackedTargets.remove(state.entityId, state);
            }
        }

        recomputeLaggingState();
        return !trackedTargets.isEmpty();
    }

    private boolean shouldLag(@Nullable TrackedTargetState state, @Nullable Vec3d nextRealPosition) {
        LivingEntity target = state == null ? null : state.entity;
        if (MC.player == null || target == null || nextRealPosition == null) {
            return false;
        }

        Vec3d currentRealPosition = currentTrackedRealPosition(state);
        double currentDistanceSq = MC.player.getPos().squaredDistanceTo(currentRealPosition);
        double nextDistanceSq = MC.player.getPos().squaredDistanceTo(nextRealPosition);
        if (nextDistanceSq > currentDistanceSq + DISTANCE_EPSILON_SQ) {
            return true;
        }
        if (nextDistanceSq < currentDistanceSq - DISTANCE_EPSILON_SQ) {
            return false;
        }
        return state.lagging;
    }

    private static Vec3d currentTrackedRealPosition(@Nullable TrackedTargetState state) {
        TrackedMovement newestMovement = state == null ? null : state.queuedMovements.peekLast();
        LivingEntity target = state == null ? null : state.entity;
        if (target == null) {
            return Vec3d.ZERO;
        }
        return newestMovement == null ? target.getPos() : newestMovement.projectedPos();
    }

    private @Nullable TrackedMovementUpdate resolveTrackedMovement(Packet<?> packet) {
        if (MC.player == null || MC.world == null || trackedTargets.isEmpty()) {
            return null;
        }

        if (packet instanceof EntityPositionSyncS2CPacket syncPacket) {
            TrackedTargetState state = trackedTargets.get(syncPacket.id());
            if (state != null) {
                return new TrackedMovementUpdate(state, new TrackedMovement(syncPacket.values().position()));
            }
        }

        if (packet instanceof EntityPositionS2CPacket positionPacket) {
            TrackedTargetState state = trackedTargets.get(positionPacket.entityId());
            if (state == null || state.entity == null) {
                return null;
            }

            LivingEntity target = state.entity;
            Vec3d basePosition = currentTrackedRealPosition(state);
            PlayerPosition base = new PlayerPosition(basePosition, target.getVelocity(), target.getYaw(), target.getPitch());
            Vec3d projected = PlayerPosition.apply(base, positionPacket.change(), positionPacket.relatives()).position();
            return new TrackedMovementUpdate(state, new TrackedMovement(projected));
        }

        if (packet instanceof EntityS2CPacket relativePacket && relativePacket.isPositionChanged()) {
            Entity entity = MC.world.getEntityById(((EntityS2CPacketAccessor) relativePacket).smoke$getId());
            if (entity instanceof LivingEntity livingTarget) {
                TrackedTargetState state = trackedTargets.get(livingTarget.getId());
                if (state == null || state.entity != livingTarget) {
                    return null;
                }

                Vec3d basePosition = currentTrackedRealPosition(state);
                Vec3d projected = basePosition.add(
                        relativePacket.getDeltaX() / DELTA_SCALE,
                        relativePacket.getDeltaY() / DELTA_SCALE,
                        relativePacket.getDeltaZ() / DELTA_SCALE
                );
                return new TrackedMovementUpdate(state, new TrackedMovement(projected));
            }
        }

        return null;
    }

    private static void releaseTrackedMovement(@Nullable TrackedTargetState state, @Nullable TrackedMovement movement) {
        if (state == null || movement == null) {
            return;
        }

        TrackedMovement oldestMovement = state.queuedMovements.peekFirst();
        if (oldestMovement == movement) {
            state.queuedMovements.pollFirst();
            return;
        }

        state.queuedMovements.removeFirstOccurrence(movement);
    }

    private boolean isPlaySessionActive() {
        return MC.player != null && MC.world != null && isPlaySession(currentConnection());
    }

    private static boolean isPlaySession(@Nullable ClientConnection connection) {
        return connection != null
                && connection.isOpen()
                && connection.getPacketListener() instanceof ClientPlayPacketListener;
    }

    private void disableOutsidePlaySession() {
        resetState();
        if (enabled()) {
            context().modules().setEnabled(this, false);
        }
    }

    private void resetState() {
        inboundQueue.clear();
        trackedTargets.clear();
        laggingPackets = false;
    }

    private void recomputeLaggingState() {
        laggingPackets = trackedTargets.values().stream().anyMatch(state -> state.lagging);
    }

    private void flushIfNoLaggingTargets() {
        recomputeLaggingState();
        if (!laggingPackets) {
            flushAll();
        }
    }

    private long resolveTrackTimeoutMillis() {
        long configuredDelay = delay.value().longValue();
        return Math.min(3000L, Math.max(1000L, configuredDelay * 4L));
    }

    private @Nullable TrackedTargetState upsertTrackedTarget(LivingEntity entity, long now) {
        TrackedTargetState state = trackedTargets.compute(entity.getId(), (ignored, existing) -> {
            if (existing == null) {
                return new TrackedTargetState(entity.getId(), entity, now);
            }

            existing.entity = entity;
            existing.lastTrackedAtMillis = now;
            return existing;
        });
        if (state != null) {
            state.lastTrackedAtMillis = now;
        }
        return state;
    }

    private @Nullable LivingEntity resolvePrimaryTrackedTarget() {
        long newestTimestamp = Long.MIN_VALUE;
        LivingEntity newestTarget = null;

        for (TrackedTargetState state : trackedTargets.values()) {
            LivingEntity entity = state.entity;
            if (entity == null || !entity.isAlive() || entity.isRemoved()) {
                continue;
            }
            if (state.lastTrackedAtMillis >= newestTimestamp) {
                newestTimestamp = state.lastTrackedAtMillis;
                newestTarget = entity;
            }
        }

        return newestTarget;
    }

    private List<RenderedGhost> collectRenderedGhosts() {
        List<RenderedGhost> ghosts = new ArrayList<>();

        for (TrackedTargetState state : trackedTargets.values()) {
            LivingEntity target = state.entity;
            if (target == null || !target.isAlive() || target.isRemoved()) {
                continue;
            }

            Vec3d realPos = getRealPosition(target);
            if (realPos == null) {
                continue;
            }

            double dx = realPos.x - target.getX();
            double dy = realPos.y - target.getY();
            double dz = realPos.z - target.getZ();
            if (dx * dx + dy * dy + dz * dz < RENDER_EPSILON_SQ) {
                continue;
            }

            double halfWidth = target.getWidth() * 0.5D;
            Box ghost = new Box(
                    realPos.x - halfWidth, realPos.y, realPos.z - halfWidth,
                    realPos.x + halfWidth, realPos.y + target.getHeight(), realPos.z + halfWidth
            );
            ghosts.add(new RenderedGhost(ghost));
        }

        return ghosts;
    }

    private record BufferedInboundPacket(
            ClientConnection connection,
            Packet<?> packet,
            @Nullable TrackedTargetState targetState,
            @Nullable TrackedMovement movement,
            long releaseAtMillis
    ) {
    }

    private record TrackedMovement(Vec3d projectedPos) {
    }

    private record TrackedMovementUpdate(TrackedTargetState targetState, TrackedMovement movement) {
    }

    private record RenderedGhost(Box box) {
    }

    private static final class TrackedTargetState {
        private final int entityId;
        private final ConcurrentLinkedDeque<TrackedMovement> queuedMovements = new ConcurrentLinkedDeque<>();
        private volatile LivingEntity entity;
        private volatile boolean lagging;
        private volatile long lastTrackedAtMillis;

        private TrackedTargetState(int entityId, LivingEntity entity, long lastTrackedAtMillis) {
            this.entityId = entityId;
            this.entity = entity;
            this.lastTrackedAtMillis = lastTrackedAtMillis;
        }
    }
}
