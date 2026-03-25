package com.smoke.client.feature.module.render;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.WorldRenderEvent;
import com.smoke.client.mixin.accessor.SimpleOptionAccessor;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.BoolSetting;
import com.smoke.client.setting.ColorSetting;
import com.smoke.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

public final class TracersModule extends Module {
    private final NumberSetting range = addSetting(new NumberSetting("range", "Range", "Maximum distance to draw tracers.", 64.0D, 1.0D, 256.0D, 1.0D));
    private final BoolSetting players = addSetting(new BoolSetting("players", "Players", "Draw tracers to players.", true));
    private final BoolSetting mobs = addSetting(new BoolSetting("mobs", "Mobs", "Draw tracers to hostile mobs.", true));
    private final BoolSetting animals = addSetting(new BoolSetting("animals", "Animals", "Draw tracers to passive and neutral entities.", false));
    private final ColorSetting playerColor = addSetting(new ColorSetting("player_color", "Player Color", "Tracer color for players.", 0xFFFF4040));
    private final ColorSetting mobColor = addSetting(new ColorSetting("mob_color", "Mob Color", "Tracer color for hostile mobs.", 0xFFFFA020));
    private final ColorSetting animalColor = addSetting(new ColorSetting("animal_color", "Animal Color", "Tracer color for passive and neutral entities.", 0xFF40C060));
    private final NumberSetting lineWidth = addSetting(new NumberSetting("line_width", "Line Width", "Tracer line thickness.", 1.5D, 0.5D, 3.0D, 0.1D));
    private final BufferAllocator allocator = new BufferAllocator(RenderLayer.getLines().getExpectedBufferSize());
    private final VertexConsumerProvider.Immediate consumers = VertexConsumerProvider.immediate(allocator);

    private SimpleFramebuffer framebuffer;
    private RawProjectionMatrix projectionMatrix;
    private boolean wasBobbing;

    public TracersModule(ModuleContext context) {
        super(context, "tracers", "Tracers", "Draws lines from the camera to nearby entities.", ModuleCategory.RENDER, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    protected void onEnable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) {
            return;
        }

        SimpleOption<Boolean> bobView = client.options.getBobView();
        wasBobbing = Boolean.TRUE.equals(bobView.getValue());
        if (wasBobbing) {
            forceBobView(client, bobView, false);
        }
    }

    @Override
    protected void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (wasBobbing && client != null && client.options != null) {
            forceBobView(client, client.options.getBobView(), true);
        }
        wasBobbing = false;

        if (framebuffer != null) {
            framebuffer.delete();
            framebuffer = null;
        }
        if (projectionMatrix != null) {
            projectionMatrix.close();
            projectionMatrix = null;
        }
    }

    @Subscribe
    private void onWorldRender(WorldRenderEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || event.context().camera() == null) {
            return;
        }

        RenderSystem.backupProjectionMatrix();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        try {
            if (projectionMatrix == null) {
                projectionMatrix = new RawProjectionMatrix("Smoke Tracers");
            }
            RenderSystem.setProjectionMatrix(projectionMatrix.set(event.context().projectionMatrix()), ProjectionType.PERSPECTIVE);
            modelViewStack.identity();
            modelViewStack.mul(event.context().positionMatrix());

            Framebuffer target = ensureFramebuffer(client);
            clearFramebuffer(target);
            GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
            GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
            RenderSystem.outputColorTextureOverride = target.getColorAttachmentView();
            RenderSystem.outputDepthTextureOverride = target.getDepthAttachmentView();

            boolean rendered = false;
            try {
                Vec3d cameraPos = event.context().camera().getPos();
                float tickDelta = event.context().tickCounter().getTickProgress(true);
                Entity cameraEntity = client.getCameraEntity();
                Vec3d startPos = cameraPos.add((cameraEntity == null ? client.player : cameraEntity).getRotationVec(tickDelta).multiply(0.05D));
                MatrixStack matrices = new MatrixStack();
                matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

                VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
                Vector3f start = new Vector3f((float) startPos.x, (float) startPos.y, (float) startPos.z);
                double rangeSq = range.value() * range.value();

                RenderSystem.lineWidth((float) lineWidth.value().doubleValue());
                try {
                    for (Entity entity : client.world.getEntities()) {
                        EspModule.Category category = EspModule.categoryOf(entity);
                        if (!shouldRender(client, entity, category, rangeSq)) {
                            continue;
                        }

                        Vec3d end = new Vec3d(
                                lerp(entity.lastRenderX, entity.getX(), tickDelta),
                                lerp(entity.lastRenderY, entity.getY(), tickDelta) + entity.getHeight() * 0.5D,
                                lerp(entity.lastRenderZ, entity.getZ(), tickDelta)
                        );
                        VertexRendering.drawVector(matrices, lines, start, end.subtract(startPos), color(category));
                        rendered = true;
                    }
                    consumers.draw();
                } finally {
                    RenderSystem.lineWidth(1.0F);
                }
            } finally {
                RenderSystem.outputColorTextureOverride = previousColor;
                RenderSystem.outputDepthTextureOverride = previousDepth;
            }

            if (rendered) {
                target.drawBlit(client.getFramebuffer().getColorAttachmentView());
            }
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private boolean shouldRender(MinecraftClient client, Entity entity, EspModule.Category category, double rangeSq) {
        return entity != null
                && entity != client.player
                && category != null
                && enabled(category)
                && entity.isAlive()
                && !entity.isRemoved()
                && client.player.squaredDistanceTo(entity) <= rangeSq;
    }

    private boolean enabled(EspModule.Category category) {
        return switch (category) {
            case PLAYERS -> players.value();
            case MOBS -> mobs.value();
            case ANIMALS -> animals.value();
        };
    }

    private int color(EspModule.Category category) {
        return switch (category) {
            case PLAYERS -> playerColor.value();
            case MOBS -> mobColor.value();
            case ANIMALS -> animalColor.value();
        };
    }

    private Framebuffer ensureFramebuffer(MinecraftClient client) {
        int width = client.getWindow().getFramebufferWidth();
        int height = client.getWindow().getFramebufferHeight();
        if (framebuffer == null) {
            return framebuffer = new SimpleFramebuffer("Smoke Tracers", width, height, true);
        }
        if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) {
            framebuffer.resize(width, height);
        }
        return framebuffer;
    }

    private static void clearFramebuffer(Framebuffer target) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(target.getColorAttachment(), 0);
        encoder.clearDepthTexture(target.getDepthAttachment(), 1.0D);
    }

    private static double lerp(double start, double end, float delta) {
        return start + (end - start) * delta;
    }

    private static void forceBobView(MinecraftClient client, SimpleOption<Boolean> bobView, boolean value) {
        bobView.setValue(value);
        ((SimpleOptionAccessor) (Object) bobView).smoke$setValue(value);
        client.options.write();
        if (client.currentScreen != null) {
            client.setScreen(client.currentScreen);
        }
    }
}
