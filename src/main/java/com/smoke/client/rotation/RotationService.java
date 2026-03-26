package com.smoke.client.rotation;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.MovementInputEvent;
import com.smoke.client.event.events.PacketOutboundEvent;
import com.smoke.client.mixin.accessor.LivingEntityAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;

import java.util.HashMap;
import java.util.Map;

public final class RotationService {
    private static final DigitalInputState[] DIGITAL_INPUT_STATES = new DigitalInputState[]{
            new DigitalInputState(false, false, false, false, Vec2f.ZERO),
            new DigitalInputState(true, false, false, false, new Vec2f(0.0F, 1.0F)),
            new DigitalInputState(false, true, false, false, new Vec2f(0.0F, -1.0F)),
            new DigitalInputState(false, false, true, false, new Vec2f(1.0F, 0.0F)),
            new DigitalInputState(false, false, false, true, new Vec2f(-1.0F, 0.0F)),
            new DigitalInputState(true, false, true, false, new Vec2f(1.0F, 1.0F).normalize()),
            new DigitalInputState(true, false, false, true, new Vec2f(-1.0F, 1.0F).normalize()),
            new DigitalInputState(false, true, true, false, new Vec2f(1.0F, -1.0F).normalize()),
            new DigitalInputState(false, true, false, true, new Vec2f(-1.0F, -1.0F).normalize())
    };

    private final Map<String, ActiveRequest> activeRequests = new HashMap<>();
    private long currentTick;
    private long sequence;

    private RotationFrame currentFrame = RotationFrame.inactive();
    private boolean frameDirty;
    private long steppedTick = Long.MIN_VALUE;

    /**
     * Tracks the "server rotation" we are currently converging toward for the active owner.
     * This is the value used for packet injection (and movement correction when silent).
     *
     * Important: we intentionally do not compute/advance this twice per tick. See beginTick/refresh.
     */
    private String activeOwnerId;
    private float serverYaw;
    private float serverPitch;
    private boolean hasServerRotation;

    /**
     * Sticky cache: allows SILENT_STICKY owners to resume from their last server rotation even if the
     * request briefly drops (e.g., module missed submitting for a tick).
     */
    private String lastOwnerId;
    private float lastServerYaw;
    private float lastServerPitch;
    private boolean hasLastServerRotation;

    /** Tracks the last rotation injected into outbound packets so we don't upgrade
     *  position-only packets when the rotation hasn't changed (avoids AimDuplicateLook). */
    private float lastInjectedYaw = Float.NaN;
    private float lastInjectedPitch = Float.NaN;

    /**
     * After a silent rotation releases, the server still keeps the last injected yaw/pitch until
     * another look packet is sent. Force a one-shot camera sync on the next move packet so movement
     * does not continue under a stale silent yaw after ownership has been dropped.
     */
    private boolean pendingCameraSync;

    /**
     * After {@link #release(String)}, vanilla may send camera rotation for one or more ticks.
     * SILENT_STICKY must not resume from {@link #lastServerYaw} until we seed from the camera again.
     */
    private boolean invalidateStickyResume;

    public void submit(RotationRequest request) {
        long expiryTick = request.persistent() ? Long.MAX_VALUE : (currentTick + Math.max(1L, request.ttlTicks()));
        activeRequests.put(
                request.ownerId(),
                new ActiveRequest(request, expiryTick, ++sequence)
        );
        frameDirty = true;
    }

    public void release(String ownerId) {
        if (ownerId == null) {
            return;
        }

        activeRequests.remove(ownerId);
        if (currentFrame.active() && currentFrame.ownerId().equals(ownerId)) {
            if (currentFrame.applyPacketRotation()) {
                pendingCameraSync = true;
            }
            currentFrame = RotationFrame.inactive();
        }
        if (activeOwnerId != null && activeOwnerId.equals(ownerId)) {
            activeOwnerId = null;
            hasServerRotation = false;
        }
        if (lastOwnerId != null && lastOwnerId.equals(ownerId)) {
            invalidateStickyResume = true;
        }
        frameDirty = true;
    }

    public void beginTick(ClientPlayerEntity player) {
        // Tick boundary: expire TTLs and publish a "current" frame without advancing rotation.
        // Rotation is advanced exactly once per tick in refresh(), after modules have submitted requests.
        advanceTick();
        steppedTick = Long.MIN_VALUE;
        rebuildFrame(player, false);
        frameDirty = false;
    }

    public void refresh(ClientPlayerEntity player) {
        // After module logic has run for this tick, step toward the current winner.
        refreshFrame(player);
        frameDirty = false;
    }

    public RotationFrame currentFrame() {
        return currentFrame;
    }

    public boolean isApplied(String ownerId, float yaw, float pitch, float toleranceDegrees) {
        if (!currentFrame.active() || !currentFrame.ownerId().equals(ownerId)) {
            return false;
        }

        ActiveRequest activeRequest = activeRequests.get(ownerId);
        boolean gcd = activeRequest == null || activeRequest.request().gcd();
        // Check against the packet rotation (what's being sent to server)
        float tolerance = gcd ? Math.max(toleranceDegrees, currentGcdStep()) : toleranceDegrees;
        float yawDelta = Math.abs(MathHelper.wrapDegrees(currentFrame.packetYaw() - yaw));
        float pitchDelta = Math.abs(currentFrame.packetPitch() - pitch);

        return yawDelta <= tolerance && pitchDelta <= tolerance;
    }

    public void applyVisibleRotations(ClientPlayerEntity player) {
        if (player == null || !currentFrame.active()) {
            return;
        }

        if (currentFrame.applyVisibleRotation()) {
            // VISIBLE: rotate camera + model to the packet rotation.
            player.setYaw(currentFrame.packetYaw());
            player.setPitch(currentFrame.packetPitch());
            player.setHeadYaw(currentFrame.packetYaw());
            player.setBodyYaw(currentFrame.packetYaw());
            return;
        }

        // SILENT: keep camera yaw/pitch untouched. Optionally align head/body yaw for third-person visuals.
        if (currentFrame.applySilentRenderRotation()) {
            float renderYaw = currentFrame.packetYaw();
            float renderPitch = currentFrame.packetPitch();
            player.setHeadYaw(renderYaw);
            player.setBodyYaw(renderYaw);
            player.lastRenderYaw = renderYaw;
            player.renderYaw = renderYaw;
            player.lastRenderPitch = renderPitch;
            player.renderPitch = renderPitch;

            // Keep vanilla interpolation state aligned with the silent render yaw.
            // Without this, the local model lerps between stale camera-facing yaw and the packet-facing yaw,
            // which looks like rapid left-right spinning in third person.
            if (player instanceof LivingEntityAccessor accessor) {
                accessor.smoke$setLastHeadYaw(renderYaw);
                accessor.smoke$setLastBodyYaw(renderYaw);
            }
        }
    }

    public void clear() {
        activeRequests.clear();
        currentFrame = RotationFrame.inactive();
        activeOwnerId = null;
        hasServerRotation = false;
        hasLastServerRotation = false;
        lastInjectedYaw = Float.NaN;
        lastInjectedPitch = Float.NaN;
        steppedTick = Long.MIN_VALUE;
        invalidateStickyResume = false;
        pendingCameraSync = false;
    }

    @Subscribe
    private void onPacketOutbound(PacketOutboundEvent event) {
        if (!(event.packet() instanceof PlayerMoveC2SPacket movePacket)) {
            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }

        if (frameDirty) {
            refreshFrame(player);
            frameDirty = false;
        }

        if (!currentFrame.active() || !currentFrame.applyPacketRotation()) {
            if (pendingCameraSync) {
                injectLook(event, movePacket, player, player.getYaw(), player.getPitch());
                pendingCameraSync = false;
                return;
            }
            lastInjectedYaw = Float.NaN;
            lastInjectedPitch = Float.NaN;
            return;
        }

        float yaw = currentFrame.packetYaw();
        float pitch = currentFrame.packetPitch();

        // Packet already contains look data — always replace with our rotation.
        if (movePacket.changesLook()) {
            if (yaw == lastInjectedYaw && pitch == lastInjectedPitch) {
                if (movePacket instanceof PlayerMoveC2SPacket.Full fullPacket) {
                    event.replace(new PlayerMoveC2SPacket.PositionAndOnGround(
                            fullPacket.getX(player.getX()),
                            fullPacket.getY(player.getY()),
                            fullPacket.getZ(player.getZ()),
                            fullPacket.isOnGround(),
                            fullPacket.horizontalCollision()
                    ));
                } else {
                    event.replace(new PlayerMoveC2SPacket.OnGroundOnly(
                            movePacket.isOnGround(),
                            movePacket.horizontalCollision()
                    ));
                }
                return;
            }

            if (movePacket instanceof PlayerMoveC2SPacket.Full fullPacket) {
                event.replace(new PlayerMoveC2SPacket.Full(
                        fullPacket.getX(player.getX()),
                        fullPacket.getY(player.getY()),
                        fullPacket.getZ(player.getZ()),
                        yaw, pitch,
                        fullPacket.isOnGround(),
                        fullPacket.horizontalCollision()
                ));
            } else {
                event.replace(new PlayerMoveC2SPacket.LookAndOnGround(
                        yaw, pitch,
                        movePacket.isOnGround(),
                        movePacket.horizontalCollision()
                ));
            }
            lastInjectedYaw = yaw;
            lastInjectedPitch = pitch;
            return;
        }

        // No look in packet — only upgrade when our rotation actually changed since last send.
        // Upgrading every position-only packet to Full with identical look triggers AimDuplicateLook.
        if (yaw == lastInjectedYaw && pitch == lastInjectedPitch) {
            return;
        }

        if (movePacket instanceof PlayerMoveC2SPacket.PositionAndOnGround positionPacket) {
            event.replace(new PlayerMoveC2SPacket.Full(
                    positionPacket.getX(player.getX()),
                    positionPacket.getY(player.getY()),
                    positionPacket.getZ(player.getZ()),
                    yaw, pitch,
                    positionPacket.isOnGround(),
                    positionPacket.horizontalCollision()
            ));
        } else {
            event.replace(new PlayerMoveC2SPacket.LookAndOnGround(
                    yaw, pitch,
                    movePacket.isOnGround(),
                    movePacket.horizontalCollision()
            ));
        }
        lastInjectedYaw = yaw;
        lastInjectedPitch = pitch;
    }

    @Subscribe
    private void onMovementInput(MovementInputEvent event) {
        if (!currentFrame.active() || !currentFrame.applyMovementCorrection()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client != null ? client.player : null;
        float cameraYaw = player != null ? player.getYaw() : currentFrame.clientYaw();
        float desiredYaw = currentFrame.movementYawOverridden()
                ? currentFrame.movementYaw()
                : cameraYaw;

        Vec2f rawMovement = event.movementVector();

        // Analog correction: only when a module explicitly overrides the movement direction.
        // Normal silent rotation must NOT rotate the analog vector — the client already computes
        // velocity via movementInputToVelocity(analog, cameraYaw).  Rotating the analog vector
        // here would double-rotate it (once here, once in movementInputToVelocity).
        if (currentFrame.movementYawOverridden()) {
            float analogDelta = MathHelper.wrapDegrees(desiredYaw - cameraYaw);
            event.setMovementVector(rotateInput(rawMovement, analogDelta));
        }

        // Digital correction: remap WASD booleans so the server's simulation — which applies
        // R(packetYaw) to PlayerInput — produces the same world-space movement direction as
        // the client, which applies R(cameraYaw) (or R(overrideYaw)) to the raw analog input.
        if (currentFrame.applyDigitalInputCorrection()) {
            PlayerInput originalInput = event.playerInput();

            float digitalDelta = MathHelper.wrapDegrees(desiredYaw - currentFrame.packetYaw());
            Vec2f correctedForServer = rotateInput(rawMovement, digitalDelta);
            DigitalInputState remappedInput = remapDigitalInput(correctedForServer);
            boolean packetForwardSprint = remappedInput.forward() && !remappedInput.backward();
            PlayerInput correctedInput = remappedInput.toPlayerInput(originalInput, packetForwardSprint && originalInput.sprint());
            if (!packetForwardSprint && player != null && player.isSprinting()) {
                player.setSprinting(false);
            }
            event.setPlayerInput(correctedInput);

            // KeyboardInput.tick() stores a raw movement vector, and ClientPlayerEntity later
            // applies movement speed factors (0.98, item-use slow, sneak slow, square-movement
            // normalization) before turning it into world velocity. To match server prediction,
            // rotate the packet-space *post-transform* vector back into camera-space, then feed
            // the inverse pre-transform vector into Input.movementVector.
            if (!currentFrame.movementYawOverridden()) {
                Vec2f serverModified = player != null
                        ? applyMovementSpeedFactors(player, remappedInput.movementVector())
                        : remappedInput.movementVector();
                Vec2f clientModified = rotateInput(serverModified, -digitalDelta);
                event.setMovementVector(player != null
                        ? invertMovementSpeedFactors(player, clientModified)
                        : clientModified);
            }
        }
    }

    private void advanceTick() {
        currentTick++;
        activeRequests.entrySet().removeIf(entry -> currentTick > entry.getValue().expiryTick());
    }

    private void refreshFrame(ClientPlayerEntity player) {
        boolean stepRotation = steppedTick != currentTick;
        rebuildFrame(player, stepRotation);
        if (stepRotation) {
            steppedTick = currentTick;
        }
    }

    private void rebuildFrame(ClientPlayerEntity player, boolean stepRotation) {
        if (player == null) {
            currentFrame = RotationFrame.inactive();
            activeOwnerId = null;
            hasServerRotation = false;
            pendingCameraSync = false;
            return;
        }

        ActiveRequest winner = chooseWinner();
        if (winner == null) {
            if (currentFrame.active() && currentFrame.applyPacketRotation()) {
                pendingCameraSync = true;
            }
            currentFrame = RotationFrame.inactive();
            activeOwnerId = null;
            hasServerRotation = false;
            return;
        }

        RotationRequest request = winner.request();

        // Seed the server rotation when the owner changes (Venom behavior: don't inherit another module's server yaw).
        // SILENT_STICKY can optionally resume from its last known server rotation to avoid "one tick drop" drift.
        seedServerRotation(player, request);

        if (stepRotation) {
            serverYaw = stepAngleToward(serverYaw, request.targetYaw(), request.maxYawStep(), true, request.gcd());
            serverPitch = MathHelper.clamp(stepAngleToward(serverPitch, request.targetPitch(), request.maxPitchStep(), false, request.gcd()), -90.0F, 90.0F);
        }

        // Cache for SILENT_STICKY continuity.
        lastOwnerId = request.ownerId();
        lastServerYaw = serverYaw;
        lastServerPitch = serverPitch;
        hasLastServerRotation = true;

        boolean applyVisible = request.mode() == RotationMode.VISIBLE;
        boolean applyMovementCorrection = request.mode() != RotationMode.VISIBLE && request.movementCorrection();
        boolean applySilentRenderRotation = request.silentRenderRotation();
        boolean applyDigitalInputCorrection = applyMovementCorrection && request.digitalInputCorrection();

        boolean movementYawOverridden = request.movementYawOverride() != null;
        float movementYaw = movementYawOverridden ? request.movementYawOverride() : player.getYaw();
        pendingCameraSync = false;

        currentFrame = new RotationFrame(
                true,
                request.ownerId(),
                player.getYaw(),
                player.getPitch(),
                movementYaw,
                movementYawOverridden,
                request.targetYaw(),
                request.targetPitch(),
                serverYaw,
                serverPitch,
                true,
                applyVisible,
                applyMovementCorrection,
                applySilentRenderRotation,
                applyDigitalInputCorrection
        );
    }

    private ActiveRequest chooseWinner() {
        ActiveRequest best = null;
        for (ActiveRequest candidate : activeRequests.values()) {
            if (best == null) {
                best = candidate;
                continue;
            }
            int pri = candidate.request().priority();
            int bestPri = best.request().priority();
            if (pri > bestPri) {
                best = candidate;
                continue;
            }
            if (pri == bestPri && candidate.sequence() > best.sequence()) {
                best = candidate;
            }
        }
        return best;
    }

    private void seedServerRotation(ClientPlayerEntity player, RotationRequest request) {
        boolean ownerChanged = activeOwnerId == null || !activeOwnerId.equals(request.ownerId());
        if (!hasServerRotation || ownerChanged) {
            boolean useSticky = request.mode() == RotationMode.SILENT_STICKY
                    && hasLastServerRotation
                    && request.ownerId().equals(lastOwnerId)
                    && !invalidateStickyResume;
            if (useSticky) {
                serverYaw = lastServerYaw;
                serverPitch = lastServerPitch;
            } else {
                serverYaw = player.getYaw();
                serverPitch = player.getPitch();
            }
            invalidateStickyResume = false;
            hasServerRotation = true;
            activeOwnerId = request.ownerId();
        }
    }

    private float stepAngleToward(float current, float target, float maxStep, boolean wrap, boolean gcd) {
        float delta = wrap ? MathHelper.wrapDegrees(target - current) : (target - current);
        float clamped = MathHelper.clamp(delta, -maxStep, maxStep);

        float step = currentGcdStep();
        if (gcd && step > 0.0F && Float.isFinite(step)) {
            float quantized = Math.round(clamped / step) * step;
            if (quantized == 0.0F && Math.abs(clamped) >= step) {
                quantized = Math.copySign(step, clamped);
            }
            if (Math.abs(quantized) > Math.abs(clamped)) {
                quantized = clamped;
            }
            clamped = quantized;
        }

        return current + clamped;
    }

    private float currentGcdStep() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return 0.15F;
        }

        double sensitivity = client.options.getMouseSensitivity().getValue();
        double scaled = sensitivity * 0.6D + 0.2D;
        return (float) (scaled * scaled * scaled * 1.2D);
    }

    private void injectLook(PacketOutboundEvent event, PlayerMoveC2SPacket movePacket, ClientPlayerEntity player, float yaw, float pitch) {
        if (movePacket instanceof PlayerMoveC2SPacket.Full fullPacket) {
            event.replace(new PlayerMoveC2SPacket.Full(
                    fullPacket.getX(player.getX()),
                    fullPacket.getY(player.getY()),
                    fullPacket.getZ(player.getZ()),
                    yaw, pitch,
                    fullPacket.isOnGround(),
                    fullPacket.horizontalCollision()
            ));
        } else if (movePacket instanceof PlayerMoveC2SPacket.PositionAndOnGround positionPacket) {
            event.replace(new PlayerMoveC2SPacket.Full(
                    positionPacket.getX(player.getX()),
                    positionPacket.getY(player.getY()),
                    positionPacket.getZ(player.getZ()),
                    yaw, pitch,
                    positionPacket.isOnGround(),
                    positionPacket.horizontalCollision()
            ));
        } else {
            event.replace(new PlayerMoveC2SPacket.LookAndOnGround(
                    yaw, pitch,
                    movePacket.isOnGround(),
                    movePacket.horizontalCollision()
            ));
        }
        lastInjectedYaw = yaw;
        lastInjectedPitch = pitch;
    }

    private static Vec2f rotateInput(Vec2f input, float deltaYaw) {
        if (input.lengthSquared() <= 1.0E-6F) {
            return input;
        }

        float radians = deltaYaw * MathHelper.RADIANS_PER_DEGREE;
        float sin = MathHelper.sin(radians);
        float cos = MathHelper.cos(radians);
        float x = input.x * cos - input.y * sin;
        float y = input.y * cos + input.x * sin;
        return new Vec2f(x, y);
    }

    private static Vec2f applyMovementSpeedFactors(ClientPlayerEntity player, Vec2f input) {
        if (input.lengthSquared() <= 1.0E-6F) {
            return input;
        }

        Vec2f adjusted = input.multiply(baseMovementScale(player));
        return applyDirectionalMovementSpeedFactors(adjusted);
    }

    private static Vec2f invertMovementSpeedFactors(ClientPlayerEntity player, Vec2f modifiedInput) {
        float magnitude = modifiedInput.length();
        if (magnitude <= 1.0E-6F) {
            return Vec2f.ZERO;
        }

        float scale = baseMovementScale(player);
        if (scale <= 1.0E-6F) {
            return Vec2f.ZERO;
        }

        Vec2f direction = modifiedInput.normalize();
        float directionalMultiplier = getDirectionalMovementSpeedMultiplier(direction);
        if (directionalMultiplier <= 1.0E-6F) {
            return Vec2f.ZERO;
        }

        float rawMagnitude = Math.max(0.0F, magnitude / (scale * directionalMultiplier));
        return direction.multiply(rawMagnitude);
    }

    private static float baseMovementScale(ClientPlayerEntity player) {
        float scale = 0.98F;
        if (player.isUsingItem() && !player.hasVehicle()) {
            scale *= 0.2F;
        }
        if (player.shouldSlowDown()) {
            scale *= (float) player.getAttributeValue(EntityAttributes.SNEAKING_SPEED);
        }
        return scale;
    }

    private static Vec2f applyDirectionalMovementSpeedFactors(Vec2f input) {
        float length = input.length();
        if (length <= 1.0E-6F) {
            return input;
        }

        Vec2f normalized = input.multiply(1.0F / length);
        float multiplier = getDirectionalMovementSpeedMultiplier(normalized);
        float adjustedLength = Math.min(length * multiplier, 1.0F);
        return normalized.multiply(adjustedLength);
    }

    private static float getDirectionalMovementSpeedMultiplier(Vec2f input) {
        float x = Math.abs(input.x);
        float y = Math.abs(input.y);
        float additional = y > x ? x / y : y / x;
        return MathHelper.sqrt(1.0F + additional * additional);
    }

    private static DigitalInputState remapDigitalInput(Vec2f corrected) {
        float correctedLen = corrected.length();
        if (correctedLen <= 1.0E-6F) {
            return DIGITAL_INPUT_STATES[0];
        }

        DigitalInputState best = DIGITAL_INPUT_STATES[0];
        float bestCost = Float.POSITIVE_INFINITY;
        for (DigitalInputState candidate : DIGITAL_INPUT_STATES) {
            Vec2f cv = candidate.movementVector();
            if (cv.lengthSquared() <= 1.0E-6F) {
                continue;
            }
            float dot = (corrected.x * cv.x + corrected.y * cv.y)
                    / (correctedLen * cv.length());
            float angularError = 1.0F - dot;
            boolean diagonal = cv.x != 0.0F && cv.y != 0.0F;
            float cost = angularError + (diagonal ? 0.001F : 0.0F);
            if (cost < bestCost) {
                best = candidate;
                bestCost = cost;
            }
        }
        return best;
    }

    private record DigitalInputState(
            boolean forward,
            boolean backward,
            boolean left,
            boolean right,
            Vec2f movementVector
    ) {
        private PlayerInput toPlayerInput(PlayerInput original, boolean sprint) {
            return new PlayerInput(
                    forward,
                    backward,
                    left,
                    right,
                    original.jump(),
                    original.sneak(),
                    sprint
            );
        }
    }

    private record ActiveRequest(RotationRequest request, long expiryTick, long sequence) {
    }
}
