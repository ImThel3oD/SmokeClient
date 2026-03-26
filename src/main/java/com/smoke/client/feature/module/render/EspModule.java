package com.smoke.client.feature.module.render;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.smoke.client.SmokeClient;
import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.EntityOutlineColorEvent;
import com.smoke.client.event.events.EntityOutlineStateEvent;
import com.smoke.client.event.events.WorldRenderEvent;
import com.smoke.client.feature.module.render.esp.EntityMaskVertexConsumerProvider;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.BoolSetting;
import com.smoke.client.setting.ColorSetting;
import com.smoke.client.setting.EnumSetting;
import com.smoke.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPipeline;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformValue;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.Handle;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class EspModule extends Module {
    private static final Identifier MASK_TARGET = Identifier.of(SmokeClient.MOD_ID, "esp_mask");
    private static final Identifier EDGE_TARGET = Identifier.of(SmokeClient.MOD_ID, "esp_edges");
    private static final Identifier RAW_TARGET = Identifier.of(SmokeClient.MOD_ID, "esp_raw");
    private static final Identifier SCENE_TARGET = Identifier.of(SmokeClient.MOD_ID, "esp_scene");
    private static final Identifier COMPOSITE_TARGET = Identifier.of(SmokeClient.MOD_ID, "esp_composite");
    private static final Identifier GLOW_TARGET = Identifier.of(SmokeClient.MOD_ID, "esp_glow");
    private static final Identifier POST_EFFECT_ID = Identifier.of(SmokeClient.MOD_ID, "esp_silhouette");
    private static final Identifier EDGE_FRAGMENT_SHADER_ID = Identifier.of(SmokeClient.MOD_ID, "post/esp_edge");
    private static final Identifier COMPOSITE_FRAGMENT_SHADER_ID = Identifier.of(SmokeClient.MOD_ID, "post/esp_composite");
    private static final Identifier FINAL_COMPOSITE_FRAGMENT_SHADER_ID = Identifier.of(SmokeClient.MOD_ID, "post/esp_scene_composite");
    private static final Identifier BLUR_FRAGMENT_SHADER_ID = Identifier.of(SmokeClient.MOD_ID, "post/esp_blur");
    private static final Identifier BLIT_VERTEX_SHADER_ID = Identifier.ofVanilla("post/blit");
    private static final Identifier BLIT_FRAGMENT_SHADER_ID = Identifier.ofVanilla("post/blit");
    private static final Identifier SOBEL_VERTEX_SHADER_ID = Identifier.ofVanilla("post/sobel");
    private static final Identifier BLUR_VERTEX_SHADER_ID = Identifier.ofVanilla("post/blur");

    private final EnumSetting<Mode> mode = addSetting(new EnumSetting<>("mode", "Mode", "ESP render mode.", Mode.class, Mode.GLOW));
    private final NumberSetting outlineWidth = addSetting(new NumberSetting(
            "outline_width",
            "Outline Width",
            "Glow outline thickness in pixels.",
            2.0D,
            1.0D,
            5.0D,
            0.5D
    ));
    private final NumberSetting opacity = addSetting(new NumberSetting(
            "opacity",
            "Opacity",
            "Glow fill opacity from 0 to 100%.",
            40.0D,
            0.0D,
            100.0D,
            1.0D
    ));
    private final BoolSetting throughWalls = addSetting(new BoolSetting(
            "through_walls",
            "Through Walls",
            "Render ESP elements without world depth occlusion.",
            true
    ));

    private final BoolSetting players = addSetting(new BoolSetting("players", "Players", "Highlight player entities.", true));
    private final ColorSetting playerColor = addSetting(new ColorSetting("player_color", "Player Color", "Color used for player ESP.", 0xFFFF4040));
    private final ColorSetting playerGlowColor = addSetting(new ColorSetting("player_glow_color", "Player Glow", "Glow color for players.", 0xFF00FFFF));
    private final BoolSetting mobs = addSetting(new BoolSetting("mobs", "Mobs", "Highlight hostile entities.", true));
    private final ColorSetting mobColor = addSetting(new ColorSetting("mob_color", "Mob Color", "Color used for hostile ESP.", 0xFFFFA020));
    private final ColorSetting mobGlowColor = addSetting(new ColorSetting("mob_glow_color", "Mob Glow", "Glow color for mobs.", 0xFFFFA020));
    private final BoolSetting animals = addSetting(new BoolSetting("animals", "Animals", "Highlight passive and neutral living entities.", true));
    private final ColorSetting animalColor = addSetting(new ColorSetting("animal_color", "Animal Color", "Color used for passive and neutral ESP.", 0xFF40C060));
    private final ColorSetting animalGlowColor = addSetting(new ColorSetting("animal_glow_color", "Animal Glow", "Glow color for animals.", 0xFF40C060));

    private final BufferAllocator boxAllocator = new BufferAllocator(RenderLayer.getLines().getExpectedBufferSize());
    private final VertexConsumerProvider.Immediate boxConsumers = VertexConsumerProvider.immediate(boxAllocator);
    private final EntityMaskVertexConsumerProvider maskConsumers = new EntityMaskVertexConsumerProvider();
    private ProjectionMatrix2 projectionMatrix;

    private PostEffectProcessor postProcessor;
    private float lastOutlineWidth = -1;
    private float lastOpacity = -1;
    private float lastGlowColor = -1;
    private boolean lastGlowMode = false;
    private SimpleFramebuffer maskFramebuffer;
    private SimpleFramebuffer boxFramebuffer;

    public EspModule(ModuleContext context) {
        super(context, "esp", "ESP", "Highlights players, mobs, and animals with boxes or clean silhouettes.", ModuleCategory.RENDER, GLFW.GLFW_KEY_UNKNOWN);
        outlineWidth.visibleWhen(() -> mode.value() != Mode.BOX);
        opacity.visibleWhen(() -> mode.value() == Mode.GLOW);
        throughWalls.visibleWhen(() -> mode.value() != Mode.OUTLINE);

        // Glow color settings only visible in GLOW mode
        playerGlowColor.visibleWhen(() -> mode.value() == Mode.GLOW);
        mobGlowColor.visibleWhen(() -> mode.value() == Mode.GLOW);
        animalGlowColor.visibleWhen(() -> mode.value() == Mode.GLOW);
    }

    @Override
    protected void onDisable() {
        closeProcessor();
        deleteFramebuffer();
    }

    @Override
    public String displaySuffix() {
        return mode.displayValue();
    }

    @Subscribe
    private void onWorldRender(WorldRenderEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }

        WorldRenderContext context = event.context();
        if (context.camera() == null || context.frustum() == null) {
            return;
        }

        if (mode.value() == Mode.BOX) {
            renderBoxes(client, context);
            return;
        }

        if (mode.value() == Mode.OUTLINE) {
            return;
        }

        renderSilhouette(client, context);
    }

    @Subscribe
    private void onEntityOutlineState(EntityOutlineStateEvent event) {
        if (shouldRenderOutlineEntity(event.entity())) {
            event.setOutlined(true);
        }
    }

    @Subscribe
    private void onEntityOutlineColor(EntityOutlineColorEvent event) {
        Entity entity = event.entity();
        if (!shouldRenderOutlineEntity(entity)) {
            return;
        }

        Category category = categoryOf(entity);
        if (category == null) {
            return;
        }

        int outlineColor = mode.value() == Mode.GLOW ? glowColor(category) : color(category);
        event.setOutlineColor(outlineColor & 0x00FFFFFF);
    }

    private void renderBoxes(MinecraftClient client, WorldRenderContext context) {
        Framebuffer framebuffer = ensureBoxFramebuffer(client);
        if (framebuffer == null) {
            return;
        }

        clearMaskFramebuffer(framebuffer, client.getFramebuffer(), throughWalls.value());

        GpuTextureView previousColorOverride = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepthOverride = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = framebuffer.getColorAttachmentView();
        RenderSystem.outputDepthTextureOverride = framebuffer.getDepthAttachmentView();

        try {
            MatrixStack matrices = new MatrixStack();
            var cameraPos = context.camera().getPos();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            VertexConsumer lines = boxConsumers.getBuffer(RenderLayer.getLines());
            float tickDelta = context.tickCounter().getTickProgress(true);

            for (Entity entity : client.world.getEntities()) {
                Category category = categoryOf(entity);
                if (!shouldRenderEntity(client, context, entity, category)) {
                    continue;
                }

                int color = color(category);
                double x = lerp(entity.lastRenderX, entity.getX(), tickDelta);
                double y = lerp(entity.lastRenderY, entity.getY(), tickDelta);
                double z = lerp(entity.lastRenderZ, entity.getZ(), tickDelta);
                var box = entity.getBoundingBox();
                double xOffset = x - entity.getX();
                double yOffset = y - entity.getY();
                double zOffset = z - entity.getZ();

                VertexRendering.drawBox(
                        matrices,
                        lines,
                        box.minX + xOffset,
                        box.minY + yOffset,
                        box.minZ + zOffset,
                        box.maxX + xOffset,
                        box.maxY + yOffset,
                        box.maxZ + zOffset,
                        red(color),
                        green(color),
                        blue(color),
                        1.0F
                );
            }

            boxConsumers.draw();
        } finally {
            RenderSystem.outputColorTextureOverride = previousColorOverride;
            RenderSystem.outputDepthTextureOverride = previousDepthOverride;
        }

        // Overlay boxes onto the main framebuffer.
        framebuffer.drawBlit(client.getFramebuffer().getColorAttachmentView());
    }

    private void renderSilhouette(MinecraftClient client, WorldRenderContext context) {
        Framebuffer framebuffer = ensureMaskFramebuffer(client);
        if (framebuffer == null) {
            return;
        }

        clearMaskFramebuffer(framebuffer, client.getFramebuffer(), throughWalls.value());

        GpuTextureView previousColorOverride = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepthOverride = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = framebuffer.getColorAttachmentView();
        RenderSystem.outputDepthTextureOverride = framebuffer.getDepthAttachmentView();

        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        var cameraPos = context.camera().getPos();
        float tickDelta = context.tickCounter().getTickProgress(true);
        MatrixStack matrices = new MatrixStack();
        boolean renderedAny = false;

        try {
            for (Entity entity : client.world.getEntities()) {
                Category category = categoryOf(entity);
                if (!shouldRenderEntity(client, context, entity, category)) {
                    continue;
                }

                renderedAny = true;
                int glowColor = glowColor(category);

                // Set color for mask capture (solid alpha; fill opacity is controlled in the composite shader).
                maskConsumers.setColor(
                        (glowColor >> 16) & 0xFF,
                        (glowColor >> 8) & 0xFF,
                        glowColor & 0xFF,
                        0xFF
                );

                double x = lerp(entity.lastRenderX, entity.getX(), tickDelta);
                double y = lerp(entity.lastRenderY, entity.getY(), tickDelta);
                double z = lerp(entity.lastRenderZ, entity.getZ(), tickDelta);

                matrices.push();
                dispatcher.render(
                        entity,
                        x - cameraPos.x,
                        y - cameraPos.y,
                        z - cameraPos.z,
                        tickDelta,
                        matrices,
                        maskConsumers,
                        dispatcher.getLight(entity, tickDelta)
                );
                matrices.pop();
            }

            if (!renderedAny) {
                return;
            }

            maskConsumers.draw();
        } finally {
            RenderSystem.outputColorTextureOverride = previousColorOverride;
            RenderSystem.outputDepthTextureOverride = previousDepthOverride;
        }

        runPostEffect(framebuffer);
    }

    private boolean shouldRenderEntity(MinecraftClient client, WorldRenderContext context, Entity entity, Category category) {
        if (entity == null || category == null || !enabled(category) || !entity.isAlive() || entity.isRemoved()) {
            return false;
        }

        if (entity == client.player) {
            return false;
        }

        if (entity == client.getCameraEntity() && !context.camera().isThirdPerson()) {
            return false;
        }

        var cameraPos = context.camera().getPos();
        return client.getEntityRenderDispatcher().shouldRender(entity, context.frustum(), cameraPos.x, cameraPos.y, cameraPos.z);
    }

    static Category categoryOf(Entity entity) {
        if (entity instanceof PlayerEntity) {
            return Category.PLAYERS;
        }
        if (!(entity instanceof LivingEntity) || entity instanceof ArmorStandEntity) {
            return null;
        }
        if (entity instanceof Monster) {
            return Category.MOBS;
        }
        if (entity instanceof Angerable angerable && angerable.hasAngerTime()) {
            return Category.MOBS;
        }
        if (entity instanceof MobEntity mob && mob.getTarget() != null) {
            return Category.MOBS;
        }
        return entity.getType().getSpawnGroup() == SpawnGroup.MONSTER ? Category.MOBS : Category.ANIMALS;
    }

    private boolean shouldRenderOutlineEntity(Entity entity) {
        Mode currentMode = mode.value();
        if (currentMode != Mode.OUTLINE && currentMode != Mode.GLOW) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || entity == null || entity == client.player) {
            return false;
        }

        Category category = categoryOf(entity);
        if (category == null || !enabled(category) || entity.isRemoved() || !entity.isAlive()) {
            return false;
        }

        return client.player.squaredDistanceTo(entity) <= 256.0D * 256.0D;
    }

    private boolean enabled(Category category) {
        return switch (category) {
            case PLAYERS -> players.value();
            case MOBS -> mobs.value();
            case ANIMALS -> animals.value();
        };
    }

    private int color(Category category) {
        return switch (category) {
            case PLAYERS -> playerColor.value();
            case MOBS -> mobColor.value();
            case ANIMALS -> animalColor.value();
        };
    }

    private int glowColor(Category category) {
        return switch (category) {
            case PLAYERS -> playerGlowColor.value();
            case MOBS -> mobGlowColor.value();
            case ANIMALS -> animalGlowColor.value();
        };
    }

    private void runPostEffect(Framebuffer framebuffer) {
        ensurePostProcessor();
        if (postProcessor == null) {
            return;
        }

        FrameGraphBuilder builder = new FrameGraphBuilder();
        PostEffectProcessor.FramebufferSet framebufferSet = new EspFramebufferSet(
                builder.createObjectNode("smoke_esp_main", MinecraftClient.getInstance().getFramebuffer()),
                builder.createObjectNode("smoke_esp_mask", framebuffer)
        );
        postProcessor.render(builder, framebuffer.textureWidth, framebuffer.textureHeight, framebufferSet);
        builder.run(ObjectAllocator.TRIVIAL);
    }

    private void ensurePostProcessor() {
        float currentOutlineWidth = (float) outlineWidth.value().doubleValue();
        float currentOpacity = mode.value() == Mode.GLOW ? (float) (opacity.value() / 100.0D) : 1.0F;
        boolean currentGlowMode = mode.value() == Mode.GLOW;

        if (postProcessor != null &&
                lastOutlineWidth == currentOutlineWidth &&
                lastOpacity == currentOpacity &&
                lastGlowMode == currentGlowMode) {
            return;
        }

        closeProcessor();

        lastOutlineWidth = currentOutlineWidth;
        lastOpacity = currentOpacity;
        lastGlowMode = currentGlowMode;

        try {
            if (projectionMatrix == null) {
                projectionMatrix = new ProjectionMatrix2("smoke_esp", 0.1F, 1000.0F, false);
            }
            postProcessor = PostEffectProcessor.parseEffect(
                    buildPipeline(currentOutlineWidth, currentOpacity, currentGlowMode),
                    MinecraftClient.getInstance().getTextureManager(),
                    Set.of(MASK_TARGET, PostEffectProcessor.MAIN),
                    POST_EFFECT_ID,
                    projectionMatrix
            );
        } catch (ShaderLoader.LoadException exception) {
            throw new RuntimeException("Failed to build ESP post effect", exception);
        }
    }

    private static PostEffectPipeline buildPipeline(float outlineWidth, float fillAlpha, boolean isGlowMode) {
        // Use outline width as the blur radius for glow spread
        float blurRadius = outlineWidth * 3.0F;

        Map<String, List<UniformValue>> blurUniformsX = Map.of(
                "BlurConfig",
                List.of(
                        new UniformValue.Vec2fValue(new Vector2f(1.0F, 0.0F)),
                        new UniformValue.FloatValue(blurRadius)
                )
        );
        Map<String, List<UniformValue>> blurUniformsY = Map.of(
                "BlurConfig",
                List.of(
                        new UniformValue.Vec2fValue(new Vector2f(0.0F, 1.0F)),
                        new UniformValue.FloatValue(blurRadius)
                )
        );
        Map<String, List<UniformValue>> blurUniformsSecondPass = Map.of(
                "BlurConfig",
                List.of(
                        new UniformValue.Vec2fValue(new Vector2f(1.0F, 0.0F)),
                        new UniformValue.FloatValue(blurRadius * 0.5F)
                )
        );
        Map<String, List<UniformValue>> blurUniformsSecondPassY = Map.of(
                "BlurConfig",
                List.of(
                        new UniformValue.Vec2fValue(new Vector2f(0.0F, 1.0F)),
                        new UniformValue.FloatValue(blurRadius * 0.5F)
                )
        );
        Map<String, List<UniformValue>> blitUniforms = Map.of(
                "BlitConfig",
                List.of(new UniformValue.Vec4fValue(new Vector4f(1.0F, 1.0F, 1.0F, 1.0F)))
        );
        Map<String, List<UniformValue>> compositeUniforms = Map.of(
                "SmokeEspConfig",
                List.of(
                        new UniformValue.FloatValue(fillAlpha),
                        new UniformValue.FloatValue(isGlowMode ? 1.0F : 0.0F),
                        new UniformValue.FloatValue(outlineWidth)
                )
        );

        return new PostEffectPipeline(
                Map.of(
                        RAW_TARGET, new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), false, 0),
                        EDGE_TARGET, new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), false, 0),
                        SCENE_TARGET, new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), false, 0),
                        COMPOSITE_TARGET, new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), false, 0),
                        GLOW_TARGET, new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), false, 0)
                ),
                List.of(
                        // Pass 1: Blit mask to raw target (contains body fill with glow color)
                        new PostEffectPipeline.Pass(
                                BLIT_VERTEX_SHADER_ID,
                                BLIT_FRAGMENT_SHADER_ID,
                                List.of(new PostEffectPipeline.TargetSampler("In", MASK_TARGET, false, false)),
                                RAW_TARGET,
                                blitUniforms
                        ),
                        // Pass 2: Edge detection - creates a presence/edge mask
                        new PostEffectPipeline.Pass(
                                SOBEL_VERTEX_SHADER_ID,
                                EDGE_FRAGMENT_SHADER_ID,
                                List.of(new PostEffectPipeline.TargetSampler("In", RAW_TARGET, false, false)),
                                EDGE_TARGET,
                                Map.of()
                        ),
                        // Pass 3: First blur pass (horizontal) - creates the glow spread
                        new PostEffectPipeline.Pass(
                                BLUR_VERTEX_SHADER_ID,
                                BLUR_FRAGMENT_SHADER_ID,
                                List.of(new PostEffectPipeline.TargetSampler("In", EDGE_TARGET, false, true)),
                                MASK_TARGET,
                                blurUniformsX
                        ),
                        // Pass 4: Second blur pass (vertical) - completes the glow
                        new PostEffectPipeline.Pass(
                                BLUR_VERTEX_SHADER_ID,
                                BLUR_FRAGMENT_SHADER_ID,
                                List.of(new PostEffectPipeline.TargetSampler("In", MASK_TARGET, false, true)),
                                GLOW_TARGET,
                                blurUniformsY
                        ),
                        // Pass 5: Third blur pass for more glow spread (horizontal)
                        new PostEffectPipeline.Pass(
                                BLUR_VERTEX_SHADER_ID,
                                BLUR_FRAGMENT_SHADER_ID,
                                List.of(new PostEffectPipeline.TargetSampler("In", GLOW_TARGET, false, true)),
                                MASK_TARGET,
                                blurUniformsSecondPass
                        ),
                        // Pass 6: Fourth blur pass (vertical)
                        new PostEffectPipeline.Pass(
                                BLUR_VERTEX_SHADER_ID,
                                BLUR_FRAGMENT_SHADER_ID,
                                List.of(new PostEffectPipeline.TargetSampler("In", MASK_TARGET, false, true)),
                                GLOW_TARGET,
                                blurUniformsSecondPassY
                        ),
                        // Pass 7: Composite the glow with the mask fill
                        new PostEffectPipeline.Pass(
                                BLIT_VERTEX_SHADER_ID,
                                COMPOSITE_FRAGMENT_SHADER_ID,
                                List.of(
                                        new PostEffectPipeline.TargetSampler("Edges", EDGE_TARGET, false, false),
                                        new PostEffectPipeline.TargetSampler("Mask", RAW_TARGET, false, false),
                                        new PostEffectPipeline.TargetSampler("Glow", GLOW_TARGET, false, false)
                                ),
                                COMPOSITE_TARGET,
                                compositeUniforms
                        ),
                        // Pass 8: Store the main scene
                        new PostEffectPipeline.Pass(
                                BLIT_VERTEX_SHADER_ID,
                                BLIT_FRAGMENT_SHADER_ID,
                                List.of(new PostEffectPipeline.TargetSampler("In", PostEffectProcessor.MAIN, false, false)),
                                SCENE_TARGET,
                                blitUniforms
                        ),
                        // Pass 9: Final composite - blend glow over scene
                        new PostEffectPipeline.Pass(
                                BLIT_VERTEX_SHADER_ID,
                                FINAL_COMPOSITE_FRAGMENT_SHADER_ID,
                                List.of(
                                        new PostEffectPipeline.TargetSampler("Scene", SCENE_TARGET, false, false),
                                        new PostEffectPipeline.TargetSampler("Overlay", COMPOSITE_TARGET, false, false)
                                ),
                                PostEffectProcessor.MAIN,
                                Map.of()
                        )
                )
        );
    }

    private Framebuffer ensureMaskFramebuffer(MinecraftClient client) {
        int width = client.getWindow().getFramebufferWidth();
        int height = client.getWindow().getFramebufferHeight();

        if (maskFramebuffer == null) {
            maskFramebuffer = new SimpleFramebuffer("Smoke ESP Mask", width, height, true);
            return maskFramebuffer;
        }

        if (maskFramebuffer.textureWidth != width || maskFramebuffer.textureHeight != height) {
            maskFramebuffer.resize(width, height);
        }
        return maskFramebuffer;
    }

    private Framebuffer ensureBoxFramebuffer(MinecraftClient client) {
        int width = client.getWindow().getFramebufferWidth();
        int height = client.getWindow().getFramebufferHeight();

        if (boxFramebuffer == null) {
            boxFramebuffer = new SimpleFramebuffer("Smoke ESP Boxes", width, height, true);
            return boxFramebuffer;
        }

        if (boxFramebuffer.textureWidth != width || boxFramebuffer.textureHeight != height) {
            boxFramebuffer.resize(width, height);
        }
        return boxFramebuffer;
    }

    private static void clearMaskFramebuffer(Framebuffer target, Framebuffer main, boolean throughWalls) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(target.getColorAttachment(), 0);
        if (throughWalls) {
            encoder.clearDepthTexture(target.getDepthAttachment(), 1.0D);
        } else {
            target.copyDepthFrom(main);
        }
    }

    private void closeProcessor() {
        if (postProcessor != null) {
            postProcessor.close();
            postProcessor = null;
        }
    }

    private void deleteFramebuffer() {
        if (maskFramebuffer != null) {
            maskFramebuffer.delete();
            maskFramebuffer = null;
        }
        if (boxFramebuffer != null) {
            boxFramebuffer.delete();
            boxFramebuffer = null;
        }
    }

    private static double lerp(double start, double end, float delta) {
        return start + (end - start) * delta;
    }

    private static float red(int color) {
        return ((color >> 16) & 0xFF) / 255.0F;
    }

    private static float green(int color) {
        return ((color >> 8) & 0xFF) / 255.0F;
    }

    private static float blue(int color) {
        return (color & 0xFF) / 255.0F;
    }

    private enum Mode {
        BOX,
        OUTLINE,
        GLOW
    }

    enum Category {
        PLAYERS,
        MOBS,
        ANIMALS
    }

    private static final class EspFramebufferSet implements PostEffectProcessor.FramebufferSet {
        private Handle<Framebuffer> main;
        private Handle<Framebuffer> mask;

        private EspFramebufferSet(Handle<Framebuffer> main, Handle<Framebuffer> mask) {
            this.main = main;
            this.mask = mask;
        }

        @Override
        public void set(Identifier id, Handle<Framebuffer> framebuffer) {
            if (PostEffectProcessor.MAIN.equals(id)) {
                main = framebuffer;
                return;
            }

            if (MASK_TARGET.equals(id)) {
                mask = framebuffer;
                return;
            }

            throw new IllegalArgumentException("Unknown framebuffer target " + id);
        }

        @Override
        public Handle<Framebuffer> get(Identifier id) {
            if (PostEffectProcessor.MAIN.equals(id)) {
                return main;
            }

            if (MASK_TARGET.equals(id)) {
                return mask;
            }

            return null;
        }
    }
}
