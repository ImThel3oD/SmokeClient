package com.smoke.client.feature.module.render;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.WorldRenderEvent;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.BoolSetting;
import com.smoke.client.setting.ColorSetting;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.TrappedChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkStatus;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ChestEspModule extends Module {
    private static final int SCAN_HORIZONTAL = 32, SCAN_VERTICAL = 4;
    private static final long SCAN_INTERVAL = 20L;
    private final BoolSetting chests = addSetting(new BoolSetting("chests", "Chests", "Highlight chests.", true));
    private final BoolSetting trappedChests = addSetting(new BoolSetting("trapped_chests", "Trapped Chests", "Highlight trapped chests.", true));
    private final BoolSetting enderChests = addSetting(new BoolSetting("ender_chests", "Ender Chests", "Highlight ender chests.", true));
    private final BoolSetting barrels = addSetting(new BoolSetting("barrels", "Barrels", "Highlight barrels.", true));
    private final BoolSetting shulkerBoxes = addSetting(new BoolSetting("shulker_boxes", "Shulker Boxes", "Highlight shulker boxes.", true));
    private final BoolSetting throughWalls = addSetting(new BoolSetting("through_walls", "Through Walls", "Render chest ESP without depth occlusion.", true));
    private final ColorSetting chestColor = addSetting(new ColorSetting("chest_color", "Chest Color", "Chest wireframe color.", 0xFFFFA500));
    private final ColorSetting trappedChestColor = addSetting(new ColorSetting("trapped_chest_color", "Trapped Chest Color", "Trapped chest wireframe color.", 0xFFFF4040));
    private final ColorSetting enderChestColor = addSetting(new ColorSetting("ender_chest_color", "Ender Chest Color", "Ender chest wireframe color.", 0xFFFF00FF));
    private final ColorSetting barrelColor = addSetting(new ColorSetting("barrel_color", "Barrel Color", "Barrel wireframe color.", 0xFFFFFF00));
    private final ColorSetting shulkerColor = addSetting(new ColorSetting("shulker_color", "Shulker Color", "Shulker wireframe color.", 0xFFFF69B4));
    private final BufferAllocator allocator = new BufferAllocator(RenderLayer.getLines().getExpectedBufferSize());
    private final VertexConsumerProvider.Immediate consumers = VertexConsumerProvider.immediate(allocator);
    private static final double EPSILON = 0.002D;
    private long lastScanTick = -1L;
    private List<BlockPos> cachedFallbackPositions = List.of();
    private SimpleFramebuffer framebuffer;
    private RawProjectionMatrix projectionMatrix;

    public ChestEspModule(ModuleContext context) {
        super(context, "chest_esp", "ChestESP", "Highlights storage block entities through walls.", ModuleCategory.RENDER, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    protected void onDisable() {
        if (framebuffer != null) framebuffer.delete();
        if (projectionMatrix != null) projectionMatrix.close();
        cachedFallbackPositions = List.of();
        lastScanTick = -1L;
        framebuffer = null;
        projectionMatrix = null;
    }

    @Subscribe
    private void onWorldRender(WorldRenderEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || event.context().camera() == null) return;

        // WorldRenderEvents.LAST is intended for immediate framebuffer writes. Explicitly set the
        // world projection/view matrices so our line pipeline projects in the expected space,
        // regardless of any render state changes that happened earlier in the frame.
        RenderSystem.backupProjectionMatrix();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        try {
            if (projectionMatrix == null) projectionMatrix = new RawProjectionMatrix("Smoke Chest ESP");
            RenderSystem.setProjectionMatrix(projectionMatrix.set(event.context().projectionMatrix()), ProjectionType.PERSPECTIVE);
            modelViewStack.identity();
            modelViewStack.mul(event.context().positionMatrix());

            Framebuffer target = ensureFramebuffer(client);
            clearFramebuffer(target, client.getFramebuffer());
            GpuTextureView prevColor = RenderSystem.outputColorTextureOverride, prevDepth = RenderSystem.outputDepthTextureOverride;
            RenderSystem.outputColorTextureOverride = target.getColorAttachmentView();
            RenderSystem.outputDepthTextureOverride = target.getDepthAttachmentView();
            var camera = event.context().camera().getPos();
            MatrixStack matrices = new MatrixStack();
            matrices.translate(-camera.x, -camera.y, -camera.z);
            VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
            int radius = client.options.getClampedViewDistance(), centerX = ChunkSectionPos.getSectionCoord(camera.x), centerZ = ChunkSectionPos.getSectionCoord(camera.z);
            boolean rendered = false;
            Set<BlockPos> seen = new HashSet<>();
            try {
                RenderSystem.lineWidth(2.0F);
                updateFallbackPositions(client);
                var manager = client.world.getChunkManager();
                for (int x = centerX - radius; x <= centerX + radius; x++) for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    var chunk = manager.getChunk(x, z, ChunkStatus.FULL, false);
                    if (chunk == null) continue;
                    for (var pos : chunk.getBlockEntityPositions()) {
                        int color = color(chunk.getBlockEntity(pos), client.world.getBlockState(pos));
                        if (color == 0) continue;
                        seen.add(pos.toImmutable());
                        rendered |= drawBox(pos, color, matrices, lines);
                    }
                }
                for (var pos : cachedFallbackPositions) if (!seen.contains(pos)) rendered |= drawBox(pos, color(null, client.world.getBlockState(pos)), matrices, lines);
                consumers.draw();
            } finally {
                RenderSystem.lineWidth(1.0F);
                RenderSystem.outputColorTextureOverride = prevColor;
                RenderSystem.outputDepthTextureOverride = prevDepth;
            }
            if (rendered) target.drawBlit(client.getFramebuffer().getColorAttachmentView());
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private void updateFallbackPositions(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        long tick = client.world.getTime();
        if (lastScanTick != -1L && tick - lastScanTick < SCAN_INTERVAL) return;
        lastScanTick = tick;
        BlockPos center = client.player.getBlockPos(), mutablePos;
        List<BlockPos> matches = new ArrayList<>();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = center.getX() - SCAN_HORIZONTAL; x <= center.getX() + SCAN_HORIZONTAL; x++) for (int y = center.getY() - SCAN_VERTICAL; y <= center.getY() + SCAN_VERTICAL; y++) for (int z = center.getZ() - SCAN_HORIZONTAL; z <= center.getZ() + SCAN_HORIZONTAL; z++) {
            mutablePos = mutable.set(x, y, z);
            if (color(null, client.world.getBlockState(mutablePos)) != 0) matches.add(mutablePos.toImmutable());
        }
        cachedFallbackPositions = List.copyOf(matches);
    }

    private boolean drawBox(BlockPos pos, int color, MatrixStack matrices, VertexConsumer lines) {
        if (color == 0) return false;
        VertexRendering.drawBox(matrices, lines, pos.getX() - EPSILON, pos.getY() - EPSILON, pos.getZ() - EPSILON, pos.getX() + 1.0 + EPSILON, pos.getY() + 1.0 + EPSILON, pos.getZ() + 1.0 + EPSILON, part(color, 16), part(color, 8), part(color, 0), part(color, 24));
        return true;
    }

    private void clearFramebuffer(Framebuffer target, Framebuffer main) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(target.getColorAttachment(), 0);
        if (throughWalls.value()) encoder.clearDepthTexture(target.getDepthAttachment(), 1.0D); else target.copyDepthFrom(main);
    }

    private int color(BlockEntity blockEntity, BlockState state) {
        if (state != null) {
            var block = state.getBlock();
            if (block instanceof TrappedChestBlock && trappedChests.value()) return trappedChestColor.value();
            if (block instanceof EnderChestBlock && enderChests.value()) return enderChestColor.value();
            if (block instanceof BarrelBlock && barrels.value()) return barrelColor.value();
            if (block instanceof ShulkerBoxBlock && shulkerBoxes.value()) return shulkerColor.value();
            if (block instanceof ChestBlock && chests.value()) return chestColor.value();
        }
        if (blockEntity != null && blockEntity.getWorld() != null) return color(null, blockEntity.getWorld().getBlockState(blockEntity.getPos()));
        return 0;
    }

    private Framebuffer ensureFramebuffer(MinecraftClient client) {
        int width = client.getWindow().getFramebufferWidth(), height = client.getWindow().getFramebufferHeight();
        if (framebuffer == null) return framebuffer = new SimpleFramebuffer("Smoke Chest ESP", width, height, true);
        if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) framebuffer.resize(width, height);
        return framebuffer;
    }

    private static float part(int color, int shift) {
        return ((color >> shift) & 255) / 255.0F;
    }
}
