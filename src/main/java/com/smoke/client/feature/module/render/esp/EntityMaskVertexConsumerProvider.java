package com.smoke.client.feature.module.render.esp;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.ColorHelper;

import java.util.LinkedHashMap;
import java.util.SequencedMap;

public final class EntityMaskVertexConsumerProvider implements VertexConsumerProvider {
    private final SequencedMap<RenderLayer, BufferAllocator> layerBuffers = createLayerBuffers();
    private final BufferAllocator fallbackAllocator = new BufferAllocator(262_144);
    private final VertexConsumerProvider.Immediate outlineDrawer = VertexConsumerProvider.immediate(layerBuffers, fallbackAllocator);

    private int red = 255;
    private int green = 255;
    private int blue = 255;
    private int alpha = 255;

    public void setColor(int red, int green, int blue, int alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    public void draw() {
        outlineDrawer.draw();
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        return new FixedColorVertexConsumer(outlineDrawer.getBuffer(layer), ColorHelper.getArgb(alpha, red, green, blue));
    }

    private static SequencedMap<RenderLayer, BufferAllocator> createLayerBuffers() {
        SequencedMap<RenderLayer, BufferAllocator> layerBuffers = new LinkedHashMap<>();
        assignLayerBuffer(layerBuffers, TexturedRenderLayers.getEntitySolid());
        assignLayerBuffer(layerBuffers, TexturedRenderLayers.getEntityCutout());
        assignLayerBuffer(layerBuffers, TexturedRenderLayers.getBannerPatterns());
        assignLayerBuffer(layerBuffers, TexturedRenderLayers.getItemEntityTranslucentCull());
        assignLayerBuffer(layerBuffers, TexturedRenderLayers.getShieldPatterns());
        assignLayerBuffer(layerBuffers, TexturedRenderLayers.getBeds());
        assignLayerBuffer(layerBuffers, TexturedRenderLayers.getShulkerBoxes());
        assignLayerBuffer(layerBuffers, TexturedRenderLayers.getSign());
        assignLayerBuffer(layerBuffers, TexturedRenderLayers.getHangingSign());
        assignLayerBuffer(layerBuffers, TexturedRenderLayers.getChest());
        assignLayerBuffer(layerBuffers, TexturedRenderLayers.getArmorTrims(false));
        assignLayerBuffer(layerBuffers, TexturedRenderLayers.getArmorTrims(true));
        assignLayerBuffer(layerBuffers, RenderLayer.getArmorEntityGlint());
        assignLayerBuffer(layerBuffers, RenderLayer.getGlint());
        assignLayerBuffer(layerBuffers, RenderLayer.getGlintTranslucent());
        assignLayerBuffer(layerBuffers, RenderLayer.getEntityGlint());
        assignLayerBuffer(layerBuffers, RenderLayer.getWaterMask());
        return layerBuffers;
    }

    private static void assignLayerBuffer(SequencedMap<RenderLayer, BufferAllocator> layerBuffers, RenderLayer layer) {
        layerBuffers.put(layer, new BufferAllocator(layer.getExpectedBufferSize()));
    }

    private static final class FixedColorVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int color;

        private FixedColorVertexConsumer(VertexConsumer delegate, int color) {
            this.delegate = delegate;
            this.color = color;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            delegate.vertex(x, y, z).color(color);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
            delegate.vertex(x, y, z, this.color, u, v, overlay, light, normalX, normalY, normalZ);
        }
    }
}
