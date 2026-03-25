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
import com.smoke.client.setting.NumberSetting;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BedBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkStatus;
import org.lwjgl.glfw.GLFW;

public final class BedEspModule extends Module {
    private static final double EPSILON = 0.002D, HEIGHT = 0.5625D;
    private static final float FADE_ALPHA = 0.35F;
    private final NumberSetting range = addSetting(new NumberSetting("range", "Range", "Maximum distance to render bed ESP.", 128.0D, 1.0D, 256.0D, 1.0D));
    private final ColorSetting color = addSetting(new ColorSetting("color", "Color", "Bed fade color.", 0xFFFF0000));
    private final BoolSetting throughWalls = addSetting(new BoolSetting("through_walls", "Through Walls", "Render bed ESP without depth occlusion.", true));
    private final BufferAllocator allocator = new BufferAllocator(RenderLayer.getDebugFilledBox().getExpectedBufferSize());
    private final VertexConsumerProvider.Immediate consumers = VertexConsumerProvider.immediate(allocator);
    private SimpleFramebuffer framebuffer;
    private RawProjectionMatrix projectionMatrix;

    public BedEspModule(ModuleContext context) {
        super(context, "bed_esp", "BedESP", "Highlights beds through walls.", ModuleCategory.RENDER, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    protected void onDisable() {
        if (framebuffer != null) framebuffer.delete();
        if (projectionMatrix != null) projectionMatrix.close();
        framebuffer = null;
        projectionMatrix = null;
    }

    @Subscribe
    private void onWorldRender(WorldRenderEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || event.context().camera() == null) return;
        RenderSystem.backupProjectionMatrix();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        try {
            if (projectionMatrix == null) projectionMatrix = new RawProjectionMatrix("Smoke Bed ESP");
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
            VertexConsumer fill = consumers.getBuffer(RenderLayer.getDebugFilledBox());
            int radius = client.options.getClampedViewDistance(), centerX = ChunkSectionPos.getSectionCoord(camera.x), centerZ = ChunkSectionPos.getSectionCoord(camera.z);
            double rangeSq = range.value() * range.value();
            boolean rendered = false;
            try {
                var manager = client.world.getChunkManager();
                for (int x = centerX - radius; x <= centerX + radius; x++) for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    var chunk = manager.getChunk(x, z, ChunkStatus.FULL, false);
                    if (chunk == null) continue;
                    for (var pos : chunk.getBlockEntityPositions()) {
                        if (!(chunk.getBlockEntity(pos) instanceof BedBlockEntity)) continue;
                        BlockState state = client.world.getBlockState(pos);
                        if (!(state.getBlock() instanceof BedBlock) || state.get(BedBlock.PART) != BedPart.FOOT || client.player.squaredDistanceTo(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > rangeSq) continue;
                        rendered |= drawBox(pos, pos.offset(state.get(BedBlock.FACING)), matrices, fill);
                    }
                }
                consumers.draw();
            } finally {
                RenderSystem.outputColorTextureOverride = prevColor;
                RenderSystem.outputDepthTextureOverride = prevDepth;
            }
            if (rendered) target.drawBlit(client.getFramebuffer().getColorAttachmentView());
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private boolean drawBox(BlockPos foot, BlockPos head, MatrixStack matrices, VertexConsumer lines) {
        int value = color.value();
        VertexRendering.drawFilledBox(matrices, lines, Math.min(foot.getX(), head.getX()) - EPSILON, foot.getY() - EPSILON, Math.min(foot.getZ(), head.getZ()) - EPSILON, Math.max(foot.getX(), head.getX()) + 1.0D + EPSILON, foot.getY() + HEIGHT + EPSILON, Math.max(foot.getZ(), head.getZ()) + 1.0D + EPSILON, part(value, 16), part(value, 8), part(value, 0), part(value, 24) * FADE_ALPHA);
        return true;
    }

    private void clearFramebuffer(Framebuffer target, Framebuffer main) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(target.getColorAttachment(), 0);
        if (throughWalls.value()) encoder.clearDepthTexture(target.getDepthAttachment(), 1.0D); else target.copyDepthFrom(main);
    }

    private Framebuffer ensureFramebuffer(MinecraftClient client) {
        int width = client.getWindow().getFramebufferWidth(), height = client.getWindow().getFramebufferHeight();
        if (framebuffer == null) return framebuffer = new SimpleFramebuffer("Smoke Bed ESP", width, height, true);
        if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) framebuffer.resize(width, height);
        return framebuffer;
    }

    private static float part(int color, int shift) {
        return ((color >> shift) & 255) / 255.0F;
    }
}
