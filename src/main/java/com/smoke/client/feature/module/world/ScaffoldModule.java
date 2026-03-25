package com.smoke.client.feature.module.world;

import com.smoke.client.event.EventPriority;
import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.HudRenderEvent;
import com.smoke.client.event.events.MovementInputEvent;
import com.smoke.client.event.events.PacketInboundEvent;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.feature.module.world.scaffold.LegitScaffoldGeometry;
import com.smoke.client.feature.module.world.scaffold.LegitScaffoldInput;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.rotation.RotationFrame;
import com.smoke.client.rotation.RotationMode;
import com.smoke.client.rotation.RotationRequest;
import com.smoke.client.setting.BoolSetting;
import com.smoke.client.setting.EnumSetting;
import com.smoke.client.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

public final class ScaffoldModule extends Module {
    private static final int ROTATION_PRIORITY = 250;
    private static final double SUPPORT_BIAS = 0.25D;
    private static final double PLACE_EDGE_EPS = 0.02D;
    private static final double ACCEL_FLOOR = 0.06D;
    private static final int PITCH_MIN = 55;
    private static final int PITCH_MAX = 89;
    private static final float NORMAL_APPLY_TOLERANCE = 1.0F;
    private static final int NORMAL_SETBACK_TICKS = 2;
    private static final Direction[] NORMAL_SUPPORT_ORDER = new Direction[]{
            Direction.DOWN,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST,
            Direction.UP
    };

    private final EnumSetting<Mode> mode = addSetting(new EnumSetting<>("mode", "Mode", "Scaffold placement mode.", Mode.class, Mode.LEGIT));
    private final BoolSetting autoSwitch = addSetting(new BoolSetting("auto_switch", "Auto-Switch", "Automatically switch to blocks in the hotbar.", true));
    private final BoolSetting keepY = addSetting(new BoolSetting("keep_y", "Keep Y", "Keep scaffold on the original bridge height.", false));
    private final EnumSetting<NormalRotateMode> normalRotateMode = addSetting(new EnumSetting<>("rotation_mode", "Rotation Mode", "How Normal and Telly scaffold rotate.", NormalRotateMode.class, NormalRotateMode.SILENT_STICKY));
    private final NumberSetting normalRotateSpeed = addSetting(new NumberSetting("rotation_speed", "Rotation Speed", "Max Normal/Telly rotation degrees per tick.", 180.0, 10.0, 180.0, 1.0));
    private final BoolSetting normalMoveFix = addSetting(new BoolSetting("movement_correction", "Movement Correction", "Remap movement while Normal/Telly use silent rotation.", true));
    private final EnumSetting<PlacementSyncMode> normalSyncMode = addSetting(new EnumSetting<>("sync_mode", "Sync Mode", "How Normal/Telly wait for rotation before placing.", PlacementSyncMode.class, PlacementSyncMode.INSTANT));
    private final EnumSetting<RaytraceMode> raytraceMode = addSetting(new EnumSetting<>("raytrace_mode", "Raytrace Mode", "Scaffold placement validation.", RaytraceMode.class, RaytraceMode.STRICT));
    private final LegitScaffoldInput input = new LegitScaffoldInput();

    private Vec2f lastMove = new Vec2f(0.0F, 0.0F);
    private boolean lastHorizontalMoveIntent;
    private boolean lastSprintIntent;
    private float bridgeYaw;
    private Vec3d bridgeDir = Vec3d.ZERO;
    private int bridgeY = Integer.MIN_VALUE;
    private BlockPos lastSupport;
    private boolean forceSneak;
    private float plannedPitch = 80.0F;
    private int restoreSlot = -1;
    private int normalSetbackTicks;
    private boolean tellyJumping;
    private BlockPos tellySupport;
    private boolean lastOnGround;

    public ScaffoldModule(ModuleContext context) {
        super(context, "scaffold", "Scaffold", "Places blocks while bridging.", ModuleCategory.WORLD, GLFW.GLFW_KEY_UNKNOWN);
        normalRotateMode.visibleWhen(() -> mode.value() == Mode.NORMAL);
        normalRotateSpeed.visibleWhen(() -> mode.value() == Mode.NORMAL);
        normalMoveFix.visibleWhen(() -> mode.value() == Mode.NORMAL && usesSilentNormalRotation());
        normalSyncMode.visibleWhen(() -> mode.value() == Mode.NORMAL);
        raytraceMode.visibleWhen(() -> mode.value() != Mode.LEGIT);
    }

    @Override
    protected void onEnable() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client != null ? client.player : null;
        bridgeYaw = player != null ? player.getYaw() : 0.0F;
        bridgeDir = player != null ? horizontal(player.getRotationVector(0.0F, bridgeYaw)) : Vec3d.ZERO;
        bridgeY = player != null ? MathHelper.floor(player.getY()) - 1 : Integer.MIN_VALUE;
        lastSupport = null;
        forceSneak = false;
        plannedPitch = 80.0F;
        normalSetbackTicks = 0;
        tellyJumping = false;
        tellySupport = null;
        lastOnGround = player != null && player.isOnGround();
        if (mode.value() == Mode.LEGIT) {
            submitLegitRotation();
        } else if (mode.value() == Mode.TELLY) {
            submitTellyRotation();
        } else {
            context().rotation().release(id());
            trace("state", normalConfigSummary());
        }
    }

    @Override
    protected void onDisable() {
        context().rotation().release(id());
        input.reset();
        restoreSlotIfNeeded(MinecraftClient.getInstance());
        bridgeDir = Vec3d.ZERO;
        bridgeY = Integer.MIN_VALUE;
        lastSupport = null;
        forceSneak = false;
        lastHorizontalMoveIntent = false;
        lastSprintIntent = false;
        normalSetbackTicks = 0;
        tellyJumping = false;
        tellySupport = null;
        lastOnGround = false;
    }

    @Override
    public String displaySuffix() {
        return mode.displayValue();
    }

    @Subscribe(priority = EventPriority.NORMAL)
    private void onTick(TickEvent event) {
        if (event.phase() != TickEvent.Phase.PRE) {
            return;
        }

        if (normalSetbackTicks > 0) {
            normalSetbackTicks--;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.interactionManager == null || client.options == null) {
            context().rotation().release(id());
            input.forceSneak(false);
            forceSneak = false;
            return;
        }

        if (!autoSwitch.value()) {
            restoreSlotIfNeeded(client);
        }

        switch (mode.value()) {
            case LEGIT -> tickLegit(client, client.player);
            case NORMAL -> tickNormal(client, client.player);
            case TELLY -> tickTelly(client, client.player);
        }
    }

    @Subscribe
    private void onPacketInbound(PacketInboundEvent event) {
        if (!enabled() || mode.value() == Mode.LEGIT) {
            return;
        }

        if (event.packet() instanceof net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket) {
            normalSetbackTicks = NORMAL_SETBACK_TICKS;
            context().rotation().release(id());
            trace("state", "setback pause=" + normalSetbackTicks);
        }
    }

    @Subscribe
    private void onHudRender(HudRenderEvent event) {
        if (!enabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.textRenderer == null) {
            return;
        }

        ItemStack displayStack = getDisplayStack(client.player);
        int blockCount = getDisplayBlockCount(client.player);
        if (displayStack == null || blockCount <= 0) {
            return;
        }

        int x = event.drawContext().getScaledWindowWidth() / 2 - 8;
        int y = event.drawContext().getScaledWindowHeight() - 60;
        event.drawContext().drawItem(displayStack, x, y);
        event.drawContext().drawStackOverlay(client.textRenderer, displayStack, x, y, String.valueOf(blockCount));
    }

    @Subscribe(priority = EventPriority.HIGHEST)
    private void onMovementInput(MovementInputEvent event) {
        lastMove = event.movementVector();
        lastHorizontalMoveIntent = horizontalMoveIntent(event.playerInput());
        lastSprintIntent = event.playerInput().sprint();
        if (!enabled()) {
            return;
        }

        if (mode.value() == Mode.TELLY) {
            PlayerInput input = event.playerInput();
            boolean sprint = input.forward() || event.movementVector().y > 0.1F;
            event.setPlayerInput(new PlayerInput(
                    input.forward(),
                    input.backward(),
                    input.left(),
                    input.right(),
                    input.jump(),
                    input.sneak(),
                    sprint
            ));
            return;
        }

        if (mode.value() != Mode.LEGIT) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client != null ? client.player : null;

        PlayerInput input = event.playerInput();
        if (player == null) {
            event.setPlayerInput(new PlayerInput(
                    input.forward(),
                    input.backward(),
                    input.left(),
                    input.right(),
                    input.jump(),
                    input.sneak(),
                    false
            ));
            return;
        }

        player.setSprinting(false);
        float yawDelta = MathHelper.wrapDegrees(player.getYaw() - lockedYaw());
        Vec2f corrected = rotateInput(event.movementVector(), yawDelta);

        float threshold = 0.3F;
        event.setPlayerInput(new PlayerInput(
                corrected.y > threshold,
                corrected.y < -threshold,
                corrected.x > threshold,
                corrected.x < -threshold,
                input.jump(),
                input.sneak(),
                false
        ));
    }

    private void tickLegit(MinecraftClient client, ClientPlayerEntity player) {
        updateBridgeDirection(player);
        BlockPos support = updateSupport(client, player);
        BlockPos underfoot = resolveLegitTarget(player, support);
        boolean underfootIntent = !player.isOnGround() && canPlaceAt(client, underfoot);
        double remainingSupport = support == null
                ? Double.POSITIVE_INFINITY
                : LegitScaffoldGeometry.remainingSupportDistance(player.getBoundingBox(), support, bridgeDir);
        boolean gapAhead = hasGapAhead(client, support, bridgeDir);

        if (!forceSneak && !underfootIntent && player.isOnGround() && gapAhead && remainingSupport <= estimateHorizontalStep(player)) {
            forceSneak = true;
        }
        input.forceSneak(forceSneak);

        plannedPitch = planPitch(client, player, lockedYaw(), support, underfoot, underfootIntent);
        submitLegitRotation();

        boolean atEdge = support != null && LegitScaffoldGeometry.isAtAbsoluteEdge(player.getBoundingBox(), support, bridgeDir, PLACE_EDGE_EPS);
        boolean shouldPlace = underfootIntent || (forceSneak && gapAhead && atEdge);
        if (shouldPlace && ensureBlockSelected(client, player) && tryPlace(client, player, lockedYaw(), plannedPitch, support, underfoot, underfootIntent)) {
            forceSneak = false;
            input.forceSneak(false);
        }
    }

    private void tickNormal(MinecraftClient client, ClientPlayerEntity player) {
        forceSneak = false;
        input.forceSneak(false);

        if (normalSetbackTicks > 0) {
            context().rotation().release(id());
            return;
        }

        NormalPlacement placement = findBestNormalPlacement(client, player);
        if (placement == null || !ensureBlockSelected(client, player)) {
            releaseNormalRotationIfIdle();
            return;
        }

        submitNormalRotation(placement);
        RotationFrame frame = acquireNormalPlacementFrame(player, placement);
        if (frame == null || !passesNormalRaytrace(client, player, frame, placement)) {
            return;
        }

        interactBlock(client, player, placement.placeHit());
    }

    private void tickTelly(MinecraftClient client, ClientPlayerEntity player) {
        forceSneak = false;
        input.forceSneak(false);

        if (normalSetbackTicks > 0) {
            context().rotation().release(id());
            return;
        }

        updateBridgeDirection(player);
        BlockPos liveSupport = updateSupport(client, player);
        if (!tellyJumping) {
            tellySupport = liveSupport;
        }
        BlockPos support = tellySupport != null ? tellySupport : liveSupport;
        BlockPos underfoot = resolveLegitTarget(player, liveSupport);
        boolean underfootIntent = false;
        double remainingSupport = liveSupport == null
                ? Double.POSITIVE_INFINITY
                : LegitScaffoldGeometry.remainingSupportDistance(player.getBoundingBox(), liveSupport, bridgeDir);
        boolean gapAhead = hasGapAhead(client, support, bridgeDir);
        boolean forwardIntent = hasTellyForwardIntent();
        boolean jump = forwardIntent && player.isOnGround() && liveSupport != null && gapAhead && remainingSupport <= estimateHorizontalStep(player);

        if (forwardIntent) {
            player.setSprinting(true);
        }
        if (jump) {
            tellySupport = liveSupport;
            player.jump();
        }
        updateTellyJumpState(player, jump, forwardIntent);

        plannedPitch = planTellyPitch(client, player, support, underfoot, underfootIntent);
        submitTellyRotation();
        RotationFrame frame = acquireTellyPlacementFrame(player);
        if (frame == null || !tellyJumping || support == null || !ensureBlockSelected(client, player)) {
            return;
        }

        BlockHitResult hit = raycast(client, player, player.getEyePos(), frame.packetYaw(), frame.packetPitch());
        if (matchesPlacement(client, hit, support, underfoot, false) && interactBlock(client, player, hit)) {
            tellySupport = hit.getBlockPos().offset(hit.getSide());
        }
    }

    private NormalPlacement findBestNormalPlacement(MinecraftClient client, ClientPlayerEntity player) {
        BlockPos primaryTarget = resolveNormalTarget(player);
        if (canPlaceAt(client, primaryTarget)) {
            NormalPlacement placement = findNormalPlacement(client, player, primaryTarget);
            if (placement != null) {
                return placement;
            }
        }

        if (!player.isOnGround()) {
            for (int dy = 1; dy <= 3; dy++) {
                BlockPos lowerTarget = primaryTarget.down(dy);
                if (canPlaceAt(client, lowerTarget)) {
                    NormalPlacement placement = findNormalPlacement(client, player, lowerTarget);
                    if (placement != null) {
                        return placement;
                    }
                }
            }
        }

        return null;
    }

    private void updateBridgeDirection(ClientPlayerEntity player) {
        if (lastMove.lengthSquared() > 1.0E-6F) {
            bridgeYaw = wrapYaw(player.getYaw() - (float) Math.toDegrees(Math.atan2(lastMove.x, lastMove.y)));
        }
        bridgeDir = horizontal(player.getRotationVector(0.0F, bridgeYaw));
    }

    private BlockPos updateSupport(MinecraftClient client, ClientPlayerEntity player) {
        Vec3d bias = bridgeDir.multiply(SUPPORT_BIAS);
        BlockPos feetPos = BlockPos.ofFloored(player.getX(), player.getY() - 0.05D, player.getZ());
        BlockPos biasedPos = BlockPos.ofFloored(player.getX() - bias.x, player.getY() - 0.05D, player.getZ() - bias.z);
        if (isValidSupport(client, biasedPos)) {
            lastSupport = biasedPos;
        } else if (isValidSupport(client, feetPos)) {
            lastSupport = feetPos;
        }
        return lastSupport;
    }

    private BlockPos resolveLegitTarget(ClientPlayerEntity player, BlockPos support) {
        if (keepY.value()) {
            if (bridgeY == Integer.MIN_VALUE) {
                bridgeY = support != null ? support.getY() : MathHelper.floor(player.getY()) - 1;
            }
            return BlockPos.ofFloored(player.getX(), bridgeY, player.getZ());
        }
        return player.getBlockPos().down();
    }

    private BlockPos resolveNormalTarget(ClientPlayerEntity player) {
        if (keepY.value()) {
            if (bridgeY == Integer.MIN_VALUE) {
                bridgeY = MathHelper.floor(player.getY()) - 1;
            }
            return BlockPos.ofFloored(player.getX(), bridgeY, player.getZ());
        }
        return player.getBlockPos().down();
    }

    private double estimateHorizontalStep(ClientPlayerEntity player) {
        double velAlong = player.getVelocity().x * bridgeDir.x + player.getVelocity().z * bridgeDir.z;
        return Math.max(Math.max(0.0D, velAlong), lastHorizontalMoveIntent ? ACCEL_FLOOR : 0.0D);
    }

    private void updateTellyJumpState(ClientPlayerEntity player, boolean jumped, boolean forwardIntent) {
        boolean onGround = player.isOnGround();
        if (jumped) {
            tellyJumping = true;
        } else if (onGround) {
            tellyJumping = false;
            tellySupport = null;
        } else if (lastOnGround && player.getVelocity().y > 0.0D && forwardIntent) {
            tellyJumping = true;
        }
        lastOnGround = onGround;
    }

    private boolean hasTellyForwardIntent() {
        return lastMove.y > 0.1F && lastHorizontalMoveIntent;
    }

    private float planTellyPitch(MinecraftClient client, ClientPlayerEntity player, BlockPos support, BlockPos underfoot, boolean underfootIntent) {
        return raytraceMode.value() == RaytraceMode.NONE
                ? 80.0F
                : planPitch(client, player, lockedYaw(), support, underfoot, underfootIntent);
    }

    private void submitTellyRotation() {
        context().rotation().submit(new RotationRequest(
                id(),
                lockedYaw(),
                plannedPitch,
                ROTATION_PRIORITY,
                RotationRequest.TTL_PERSISTENT,
                0.0F,
                0.0F,
                RotationMode.SILENT_STICKY,
                true,
                bridgeYaw,
                true,
                true,
                true
        ));
    }

    private RotationFrame acquireTellyPlacementFrame(ClientPlayerEntity player) {
        context().rotation().refresh(player);
        RotationFrame frame = context().rotation().currentFrame();
        return ownsNormalRotation(frame) ? frame : null;
    }

    private void submitLegitRotation() {
        context().rotation().submit(new RotationRequest(
                id(),
                lockedYaw(),
                plannedPitch,
                ROTATION_PRIORITY,
                RotationRequest.TTL_PERSISTENT,
                0.0F,
                0.0F,
                RotationMode.SILENT_STICKY,
                false,
                null,
                true,
                false
        ));
    }

    private void submitNormalRotation(NormalPlacement placement) {
        RotationMode rotationMode = switch (normalRotateMode.value()) {
            case SILENT_STICKY -> RotationMode.SILENT_STICKY;
            case SILENT -> RotationMode.SILENT;
            case VISIBLE -> RotationMode.VISIBLE;
        };
        boolean movementCorrection = usesSilentNormalRotation() && normalMoveFix.value();
        float speed = normalRotateSpeed.value().floatValue();
        context().rotation().submit(new RotationRequest(
                id(),
                placement.yaw(),
                placement.pitch(),
                ROTATION_PRIORITY,
                RotationRequest.TTL_PERSISTENT,
                speed,
                speed,
                rotationMode,
                movementCorrection,
                null,
                rotationMode != RotationMode.VISIBLE,
                movementCorrection,
                true
        ));
    }

    private RotationFrame acquireNormalPlacementFrame(ClientPlayerEntity player, NormalPlacement placement) {
        if (normalSyncMode.value() == PlacementSyncMode.INSTANT) {
            context().rotation().refresh(player);
            if (normalRotateMode.value() == NormalRotateMode.VISIBLE) {
                context().rotation().applyVisibleRotations(player);
            }
            RotationFrame frame = context().rotation().currentFrame();
            return ownsNormalRotation(frame) ? frame : null;
        }

        RotationFrame frame = context().rotation().currentFrame();
        if (!ownsNormalRotation(frame)) {
            return null;
        }
        return context().rotation().isApplied(id(), placement.yaw(), placement.pitch(), NORMAL_APPLY_TOLERANCE) ? frame : null;
    }

    private float lockedYaw() {
        return wrapYaw(bridgeYaw + 180.0F);
    }

    private float planPitch(MinecraftClient client, ClientPlayerEntity player, float yaw, BlockPos support, BlockPos underfoot, boolean underfootIntent) {
        if (!underfootIntent && support == null) {
            return plannedPitch;
        }

        float bestPitch = plannedPitch;
        double bestDelta = Double.POSITIVE_INFINITY;
        Vec3d eye = player.getEyePos();
        for (int pitch = PITCH_MIN; pitch <= PITCH_MAX; pitch++) {
            BlockHitResult hit = raycast(client, player, eye, yaw, pitch);
            if (matchesPlacement(client, hit, support, underfoot, underfootIntent)) {
                double delta = Math.abs(pitch - plannedPitch);
                if (delta < bestDelta) {
                    bestPitch = pitch;
                    bestDelta = delta;
                }
            }
        }
        return MathHelper.clamp(bestPitch, -90.0F, 90.0F);
    }

    private boolean tryPlace(MinecraftClient client, ClientPlayerEntity player, float yaw, float pitch, BlockPos support, BlockPos underfoot, boolean underfootIntent) {
        BlockHitResult hit = raycast(client, player, player.getEyePos(), yaw, pitch);
        return matchesPlacement(client, hit, support, underfoot, underfootIntent) && interactBlock(client, player, hit);
    }

    private boolean ensureBlockSelected(MinecraftClient client, ClientPlayerEntity player) {
        if (!autoSwitch.value()) {
            return isValidBlockStack(player.getMainHandStack());
        }

        PlayerInventory inventory = player.getInventory();
        int slot = findBlockSlot(inventory);
        if (slot < 0) {
            return false;
        }
        if (slot == inventory.getSelectedSlot()) {
            return true;
        }
        if (restoreSlot < 0) {
            restoreSlot = inventory.getSelectedSlot();
        }
        inventory.setSelectedSlot(slot);
        return true;
    }

    private ItemStack getDisplayStack(ClientPlayerEntity player) {
        if (autoSwitch.value()) {
            int slot = findBlockSlot(player.getInventory());
            return slot < 0 ? ItemStack.EMPTY : player.getInventory().getStack(slot);
        }
        return player.getMainHandStack();
    }

    private int getDisplayBlockCount(ClientPlayerEntity player) {
        if (!autoSwitch.value()) {
            return isValidBlockStack(player.getMainHandStack()) ? player.getMainHandStack().getCount() : 0;
        }

        int count = 0;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (isValidBlockStack(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int findBlockSlot(PlayerInventory inventory) {
        int selected = inventory.getSelectedSlot();
        if (isValidBlockStack(inventory.getStack(selected))) {
            return selected;
        }
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            if (slot != selected && isValidBlockStack(inventory.getStack(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private void restoreSlotIfNeeded(MinecraftClient client) {
        if (restoreSlot < 0 || client == null || client.player == null) {
            return;
        }
        client.player.getInventory().setSelectedSlot(restoreSlot);
        restoreSlot = -1;
    }

    private NormalPlacement findNormalPlacement(MinecraftClient client, ClientPlayerEntity player, BlockPos targetPos) {
        Vec3d eye = player.getEyePos();
        boolean alignYaw = usesSilentNormalRotation() && normalMoveFix.value();

        for (Direction direction : NORMAL_SUPPORT_ORDER) {
            BlockPos supportPos = targetPos.offset(direction);
            if (!isValidSupport(client, supportPos)) {
                continue;
            }

            Direction supportFace = direction.getOpposite();
            Vec3d aimPoint = faceCenter(supportPos, supportFace);

            if (alignYaw) {
                Vec3d aligned = findMovementAlignedAimPoint(eye, supportPos, supportFace, player.getYaw());
                if (aligned != null) {
                    aimPoint = aligned;
                }
            }

            float[] angles = aimAngles(eye, aimPoint);
            return new NormalPlacement(
                    supportPos,
                    supportFace,
                    new BlockHitResult(aimPoint, supportFace, supportPos, false),
                    angles[0],
                    angles[1]
            );
        }
        return null;
    }

    private static Vec3d findMovementAlignedAimPoint(Vec3d eye, BlockPos supportPos, Direction supportFace, float cameraYaw) {
        Vec3d center = faceCenter(supportPos, supportFace);
        float defaultYaw = aimAngles(eye, center)[0];

        float delta = MathHelper.wrapDegrees(cameraYaw - defaultYaw);
        float snappedDelta = Math.round(delta / 45.0F) * 45.0F;
        if (Math.abs(delta - snappedDelta) < 1.0F) {
            return null;
        }

        float idealYaw = wrapYaw(cameraYaw - snappedDelta);
        double yawRad = Math.toRadians(idealYaw);
        double hx = -Math.sin(yawRad);
        double hz = Math.cos(yawRad);

        double margin = 0.15D;
        double fMinX = supportPos.getX() + margin;
        double fMaxX = supportPos.getX() + 1.0D - margin;
        double fMinZ = supportPos.getZ() + margin;
        double fMaxZ = supportPos.getZ() + 1.0D - margin;

        Direction.Axis axis = supportFace.getAxis();

        if (axis == Direction.Axis.Y) {
            double tMin = 0.001D;
            double tMax = 100.0D;
            if (Math.abs(hx) > 1.0E-9D) {
                double t1 = (fMinX - eye.x) / hx;
                double t2 = (fMaxX - eye.x) / hx;
                if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
            } else if (eye.x < fMinX || eye.x > fMaxX) {
                return null;
            }
            if (Math.abs(hz) > 1.0E-9D) {
                double t1 = (fMinZ - eye.z) / hz;
                double t2 = (fMaxZ - eye.z) / hz;
                if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
            } else if (eye.z < fMinZ || eye.z > fMaxZ) {
                return null;
            }
            if (tMin > tMax) {
                return null;
            }
            double t = (tMin + tMax) * 0.5D;
            double fy = supportFace == Direction.UP ? supportPos.getY() + 1.0D : supportPos.getY();
            return new Vec3d(eye.x + t * hx, fy, eye.z + t * hz);
        }

        if (axis == Direction.Axis.Z) {
            double fz = supportFace == Direction.SOUTH ? supportPos.getZ() + 1.0D : supportPos.getZ();
            if (Math.abs(hz) < 1.0E-9D) {
                return null;
            }
            double t = (fz - eye.z) / hz;
            if (t <= 0.0D) {
                return null;
            }
            double ax = eye.x + t * hx;
            if (ax < fMinX || ax > fMaxX) {
                return null;
            }
            return new Vec3d(ax, supportPos.getY() + 0.5D, fz);
        }

        double fx = supportFace == Direction.EAST ? supportPos.getX() + 1.0D : supportPos.getX();
        if (Math.abs(hx) < 1.0E-9D) {
            return null;
        }
        double t = (fx - eye.x) / hx;
        if (t <= 0.0D) {
            return null;
        }
        double az = eye.z + t * hz;
        if (az < fMinZ || az > fMaxZ) {
            return null;
        }
        return new Vec3d(fx, supportPos.getY() + 0.5D, az);
    }

    private boolean matchesPlacement(MinecraftClient client, BlockHitResult hit, BlockPos support, BlockPos underfoot, boolean underfootIntent) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        BlockPos placePos = hit.getBlockPos().offset(hit.getSide());
        if (!canPlaceAt(client, placePos)) {
            return false;
        }

        return underfootIntent ? placePos.equals(underfoot) : support != null && matchesForwardPlacement(placePos, support);
    }

    private boolean matchesForwardPlacement(BlockPos placePos, BlockPos support) {
        if (placePos.getY() != support.getY()) {
            return false;
        }

        int dx = placePos.getX() - support.getX();
        int dz = placePos.getZ() - support.getZ();
        return Math.abs(dx) + Math.abs(dz) == 1 && (dx * bridgeDir.x + dz * bridgeDir.z) > 0.1D;
    }

    private boolean passesNormalRaytrace(MinecraftClient client, ClientPlayerEntity player, RotationFrame frame, NormalPlacement placement) {
        if (raytraceMode.value() == RaytraceMode.NONE) {
            return true;
        }

        float yaw = frame.packetYaw();
        float pitch = frame.packetPitch();

        return matchesNormalRaytrace(raycast(client, player, player.getEyePos(), yaw, pitch), placement);
    }

    private boolean matchesNormalRaytrace(BlockHitResult hit, NormalPlacement placement) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        if (!hit.getBlockPos().equals(placement.supportPos())) {
            return false;
        }
        return raytraceMode.value() == RaytraceMode.LENIENT || hit.getSide() == placement.supportFace();
    }

    private boolean interactBlock(MinecraftClient client, ClientPlayerEntity player, BlockHitResult hit) {
        ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        if (!result.isAccepted()) {
            return false;
        }

        player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    private void releaseNormalRotationIfIdle() {
        if (normalRotateMode.value() != NormalRotateMode.SILENT_STICKY) {
            context().rotation().release(id());
        }
    }

    private boolean ownsNormalRotation(RotationFrame frame) {
        return frame.active() && id().equals(frame.ownerId());
    }

    private boolean usesSilentNormalRotation() {
        return normalRotateMode.value() != NormalRotateMode.VISIBLE;
    }

    private String normalConfigSummary() {
        return "config rot=" + normalRotateMode.displayValue()
                + " speed=" + normalRotateSpeed.displayValue()
                + " move=" + normalMoveFix.value()
                + " sync=" + normalSyncMode.displayValue()
                + " ray=" + raytraceMode.displayValue()
                + " keep_y=" + keepY.value();
    }

    private static BlockHitResult raycast(MinecraftClient client, ClientPlayerEntity player, Vec3d start, float yaw, float pitch) {
        Vec3d end = start.add(player.getRotationVector(pitch, yaw).multiply(player.getBlockInteractionRange()));
        return client.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
    }

    private static boolean isValidSupport(MinecraftClient client, BlockPos pos) {
        if (!client.world.getWorldBorder().contains(pos)) {
            return false;
        }
        BlockState state = client.world.getBlockState(pos);
        return !state.isAir() && !state.isReplaceable() && !state.getCollisionShape(client.world, pos).isEmpty();
    }

    private static boolean canPlaceAt(MinecraftClient client, BlockPos pos) {
        if (!client.world.getWorldBorder().contains(pos)) {
            return false;
        }
        BlockState state = client.world.getBlockState(pos);
        return state.isAir() || state.isReplaceable();
    }

    private static boolean hasGapAhead(MinecraftClient client, BlockPos support, Vec3d dir) {
        if (support == null) {
            return false;
        }
        boolean gapX = Math.abs(dir.x) > 0.3D && canPlaceAt(client, support.add(dir.x > 0.0D ? 1 : -1, 0, 0));
        boolean gapZ = Math.abs(dir.z) > 0.3D && canPlaceAt(client, support.add(0, 0, dir.z > 0.0D ? 1 : -1));
        return gapX || gapZ;
    }

    private static Vec3d faceCenter(BlockPos pos, Direction face) {
        return Vec3d.ofCenter(pos).add(Vec3d.of(face.getVector()).multiply(0.5D));
    }

    private static float[] aimAngles(Vec3d eye, Vec3d target) {
        Vec3d delta = target.subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        return new float[]{
                wrapYaw((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D)),
                MathHelper.clamp((float) -Math.toDegrees(Math.atan2(delta.y, horizontal)), -90.0F, 90.0F)
        };
    }

    private static boolean isValidBlockStack(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && !(blockItem.getBlock() instanceof FallingBlock);
    }

    private static boolean horizontalMoveIntent(PlayerInput input) {
        return input.forward() || input.backward() || input.left() || input.right();
    }

    private static float wrapYaw(float yaw) {
        float wrapped = MathHelper.wrapDegrees(yaw);
        return wrapped == -180.0F ? 180.0F : wrapped;
    }

    private static Vec3d horizontal(Vec3d dir) {
        double length = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        return length <= 1.0E-8D ? Vec3d.ZERO : new Vec3d(dir.x / length, 0.0D, dir.z / length);
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

    private enum Mode {
        LEGIT,
        NORMAL,
        TELLY
    }

    private enum NormalRotateMode {
        SILENT_STICKY,
        SILENT,
        VISIBLE
    }

    private enum PlacementSyncMode {
        INSTANT,
        SERVER
    }

    private enum RaytraceMode {
        STRICT,
        LENIENT,
        NONE
    }

    private record NormalPlacement(
            BlockPos supportPos,
            Direction supportFace,
            BlockHitResult placeHit,
            float yaw,
            float pitch
    ) {
    }
}
