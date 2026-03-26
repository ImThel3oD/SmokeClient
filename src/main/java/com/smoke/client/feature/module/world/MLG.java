package com.smoke.client.feature.module.world;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.TickEvent;
import com.smoke.client.mixin.accessor.MinecraftClientAccessor;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.rotation.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

public final class MLG extends Module {
    private static final float MIN_FALL = 3.0F, DOWN = 90.0F;
    private static final int PRIORITY = 300, COLLECT_TIMEOUT = 8;
    private int restoreSlot = -1, bucketSlot = -1, collectTicks;
    private BlockPos waterPos;
    private BlockHitResult aimHit;
    public MLG(ModuleContext context) { super(context, "mlg", "MLG", "Places and picks water during dangerous falls.", ModuleCategory.WORLD, GLFW.GLFW_KEY_UNKNOWN); }
    @Override protected void onDisable() { reset(true); }
    @Subscribe
    private void onTick(TickEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.interactionManager == null) { if (event.phase() == TickEvent.Phase.PRE) reset(true); return; }
        if (event.phase() == TickEvent.Phase.PRE) pre(client, client.player); else if (aimHit != null || waterPos != null) post(client, client.player);
    }

    private void pre(MinecraftClient client, ClientPlayerEntity player) {
        if (waterPos != null) { aimHit = new BlockHitResult(Vec3d.ofCenter(waterPos), Direction.UP, waterPos, false); rotate(player); return; }
        if (!shouldUse(player)) { reset(true); return; }
        int slot = findBucket(player.getInventory());
        if (slot < 0) { reset(true); return; }
        if (restoreSlot < 0) restoreSlot = player.getInventory().getSelectedSlot();
        bucketSlot = slot; player.getInventory().setSelectedSlot(slot); rotate(player);
        BlockHitResult hit = groundHit(client, player);
        if (hit == null || !placeable(client, hit.getBlockPos().up())) { aimHit = null; return; }
        aimHit = hit;
        sendLook(player);
        use(client, hit);
        if (player.getInventory().getStack(bucketSlot).isOf(Items.BUCKET)) {
            waterPos = hit.getBlockPos().up();
            aimHit = new BlockHitResult(Vec3d.ofCenter(waterPos), Direction.UP, waterPos, false);
            collectTicks = 0;
        }
    }

    private void post(MinecraftClient client, ClientPlayerEntity player) {
        if (aimHit != null) client.crosshairTarget = aimHit;
        if (waterPos == null) return;
        rotate(player);
        if (!player.isOnGround() || ++collectTicks > COLLECT_TIMEOUT || bucketSlot < 0) { if (collectTicks > COLLECT_TIMEOUT) reset(true); return; }
        player.getInventory().setSelectedSlot(bucketSlot);
        if (!player.getInventory().getStack(bucketSlot).isOf(Items.BUCKET)) return;
        sendLook(player);
        use(client, aimHit);
        if (player.getInventory().getStack(bucketSlot).isOf(Items.WATER_BUCKET)) reset(true);
    }

    private void rotate(ClientPlayerEntity player) {
        player.setPitch(DOWN);
        context().rotation().submit(new RotationRequest(id(), player.getYaw(), DOWN, PRIORITY, RotationRequest.TTL_PERSISTENT, 0.0F, 0.0F, RotationMode.VISIBLE, false, false));
    }

    private void sendLook(ClientPlayerEntity player) {
        context().packets().send(new PlayerMoveC2SPacket.LookAndOnGround(
                player.getYaw(),
                DOWN,
                player.isOnGround(),
                player.horizontalCollision
        ));
    }

    private static boolean shouldUse(ClientPlayerEntity player) {
        return !player.isOnGround() && !player.isTouchingWater() && !player.isInLava() && !player.getAbilities().flying && player.fallDistance > MIN_FALL;
    }

    private static int findBucket(PlayerInventory inventory) {
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) if (inventory.getStack(slot).isOf(Items.WATER_BUCKET)) return slot;
        return -1;
    }

    private static BlockHitResult groundHit(MinecraftClient client, ClientPlayerEntity player) {
        Vec3d eye = player.getEyePos().add(player.getVelocity());
        HitResult hit = client.world.raycast(new RaycastContext(eye, eye.add(player.getRotationVector(DOWN, player.getYaw()).multiply(player.getBlockInteractionRange())), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        double x = MathHelper.clamp(player.getX(), pos.getX() + 0.05D, pos.getX() + 0.95D), z = MathHelper.clamp(player.getZ(), pos.getZ() + 0.05D, pos.getZ() + 0.95D);
        return new BlockHitResult(new Vec3d(x, pos.getY() + 1.0D, z), Direction.UP, pos, false);
    }

    private static boolean placeable(MinecraftClient client, BlockPos pos) {
        return client.world.getWorldBorder().contains(pos) && client.world.getBlockState(pos).isReplaceable() && client.world.getFluidState(pos).isEmpty();
    }

    private static void use(MinecraftClient client, BlockHitResult hit) {
        HitResult prev = client.crosshairTarget;
        client.crosshairTarget = hit;
        ((MinecraftClientAccessor) (Object) client).smoke$doItemUse();
        client.crosshairTarget = prev;
    }
    private void reset(boolean restore) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (restore && client.player != null && restoreSlot >= 0) client.player.getInventory().setSelectedSlot(restoreSlot);
        context().rotation().release(id());
        restoreSlot = bucketSlot = -1; collectTicks = 0; waterPos = null; aimHit = null;
    }
}
