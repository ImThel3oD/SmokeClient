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
import com.smoke.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

public final class NameTagsModule extends Module {
    private static NameTagsModule instance;

    private static final float LABEL_SCALE = 0.025F;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int BAR_WIDTH = 3;
    private static final int ITEM_SIZE = 10;
    private static final EquipmentSlot[] EQUIPMENT_SLOTS = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    private final NumberSetting range = addSetting(new NumberSetting(
            "range", "Range", "Maximum render distance for name tags.", 32.0D, 1.0D, 64.0D, 1.0D
    ));
    private final BoolSetting armor = addSetting(new BoolSetting(
            "armor", "Armor", "Show equipped armor and held items above the name tag.", true
    ));
    private final BoolSetting players = addSetting(new BoolSetting(
            "players", "Players", "Show name tags for player entities.", true
    ));
    private final BoolSetting mobs = addSetting(new BoolSetting(
            "mobs", "Mobs", "Show name tags for hostile entities.", true
    ));
    private final BoolSetting animals = addSetting(new BoolSetting(
            "animals", "Animals", "Show name tags for passive and neutral entities.", false
    ));

    private final SequencedMap<RenderLayer, BufferAllocator> layerBuffers = createLayerBuffers();
    private final BufferAllocator fallbackAllocator = new BufferAllocator(262_144);
    private final VertexConsumerProvider.Immediate consumers = VertexConsumerProvider.immediate(layerBuffers, fallbackAllocator);
    private final ItemRenderState itemRenderState = new ItemRenderState();
    private final ItemStack[] equipmentBuffer = new ItemStack[EQUIPMENT_SLOTS.length];
    private final List<EntitySnapshot> snapshots = new ArrayList<>();
    private SimpleFramebuffer framebuffer;
    private RawProjectionMatrix projectionMatrix;

    public NameTagsModule(ModuleContext context) {
        super(context, "nametags", "NameTags",
                "Renders custom name tags with health and equipment above entities.",
                ModuleCategory.RENDER, GLFW.GLFW_KEY_UNKNOWN);
        instance = this;
    }

    @Override
    protected void onDisable() {
        consumers.draw();
        fallbackAllocator.reset();
        layerBuffers.values().forEach(BufferAllocator::reset);
        itemRenderState.clear();
        snapshots.clear();
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
        if (client.world == null || client.player == null) {
            return;
        }

        WorldRenderContext context = event.context();
        if (context.camera() == null || context.frustum() == null) {
            return;
        }

        double maxDistSq = range.value() * range.value();
        var cameraPos = context.camera().getPos();
        float tickDelta = context.tickCounter().getTickProgress(true);

        snapshots.clear();
        for (Entity entity : client.world.getEntities()) {
            if (!shouldRender(client, context, entity, cameraPos.x, cameraPos.y, cameraPos.z, maxDistSq)) {
                continue;
            }
            double x = lerp(entity.lastRenderX, entity.getX(), tickDelta);
            double y = lerp(entity.lastRenderY, entity.getY(), tickDelta);
            double z = lerp(entity.lastRenderZ, entity.getZ(), tickDelta);
            snapshots.add(new EntitySnapshot(
                    entity,
                    x - cameraPos.x,
                    y - cameraPos.y + entity.getHeight() + 0.5,
                    z - cameraPos.z
            ));
        }

        if (snapshots.isEmpty()) {
            return;
        }

        RenderSystem.backupProjectionMatrix();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();

        try {
            if (projectionMatrix == null) {
                projectionMatrix = new RawProjectionMatrix("Smoke NameTags");
            }
            RenderSystem.setProjectionMatrix(
                    projectionMatrix.set(context.projectionMatrix()), ProjectionType.PERSPECTIVE
            );
            modelViewStack.identity();
            modelViewStack.mul(context.positionMatrix());

            Framebuffer target = ensureFramebuffer(client);
            clearFramebuffer(target);

            GpuTextureView previousColorOverride = RenderSystem.outputColorTextureOverride;
            GpuTextureView previousDepthOverride = RenderSystem.outputDepthTextureOverride;
            RenderSystem.outputColorTextureOverride = target.getColorAttachmentView();
            RenderSystem.outputDepthTextureOverride = target.getDepthAttachmentView();

            try {
                TextRenderer textRenderer = client.textRenderer;
                var cameraRotation = client.getEntityRenderDispatcher().getRotation();
                MatrixStack matrices = new MatrixStack();

                // WorldRenderEvents.LAST requires direct framebuffer writes. Rendering into a cleared-depth
                // target keeps the entire tag visible through walls without touching earlier world passes.
                for (EntitySnapshot snap : snapshots) {
                    matrices.push();
                    matrices.translate(snap.rx, snap.ry, snap.rz);
                    matrices.multiply(cameraRotation);
                    matrices.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

                    Matrix4f posMatrix = matrices.peek().getPositionMatrix();
                    Text name = snap.entity.getDisplayName();
                    int nameWidth = textRenderer.getWidth(name);
                    float nameX = -nameWidth / 2.0F;

                    textRenderer.draw(
                            name, nameX, 0, 0xFFFFFFFF, true,
                            posMatrix, consumers,
                            TextRenderer.TextLayerType.SEE_THROUGH, 0, FULL_BRIGHT
                    );

                    if (snap.entity instanceof LivingEntity living) {
                        renderHealthBar(posMatrix, living, nameWidth);
                    }

                    matrices.pop();
                }
                consumers.draw();

                if (armor.value()) {
                    for (EntitySnapshot snap : snapshots) {
                        if (!(snap.entity instanceof LivingEntity living)) {
                            continue;
                        }

                        matrices.push();
                        matrices.translate(snap.rx, snap.ry, snap.rz);
                        matrices.multiply(cameraRotation);
                        matrices.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

                        renderEquipment(matrices, living, client);

                        matrices.pop();
                    }
                    consumers.draw();
                }
            } finally {
                RenderSystem.outputColorTextureOverride = previousColorOverride;
                RenderSystem.outputDepthTextureOverride = previousDepthOverride;
            }

            target.drawBlit(client.getFramebuffer().getColorAttachmentView());
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private void renderHealthBar(Matrix4f posMatrix, LivingEntity entity, int nameWidth) {
        float health = entity.getHealth();
        float maxHealth = entity.getMaxHealth();
        if (maxHealth <= 0) {
            return;
        }

        float fraction = Math.clamp(health / maxHealth, 0.0F, 1.0F);
        float halfName = nameWidth / 2.0F;
        float halfBody = (entity.getWidth() * 0.5F) / LABEL_SCALE;
        float barX = -Math.max(halfName, halfBody) - 6.0F;
        float barTop = 0.0F;
        float barBottom = (entity.getHeight() + 0.5F) / LABEL_SCALE;

        fillRect(posMatrix, barX, barTop, barX + BAR_WIDTH, barBottom, 0x60000000);

        int fillColor = healthColor(fraction);
        float filledTop = barBottom - (barBottom - barTop) * fraction;
        fillRect(posMatrix, barX, filledTop, barX + BAR_WIDTH, barBottom, fillColor);
    }

    private void renderEquipment(MatrixStack matrices, LivingEntity entity, MinecraftClient client) {
        int count = 0;
        for (EquipmentSlot slot : EQUIPMENT_SLOTS) {
            ItemStack stack = entity.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                equipmentBuffer[count++] = stack;
            }
        }
        if (count == 0) {
            return;
        }

        float totalWidth = count * ITEM_SIZE + (count - 1) * 2;
        float startX = -totalWidth / 2.0F;
        float itemY = -ITEM_SIZE - 2;

        for (int i = 0; i < count; i++) {
            ItemStack stack = equipmentBuffer[i];
            float itemX = startX + i * (ITEM_SIZE + 2);

            matrices.push();
            matrices.translate(itemX + ITEM_SIZE / 2.0F, itemY + ITEM_SIZE / 2.0F, 0);
            matrices.scale(ITEM_SIZE, -ITEM_SIZE, 1);

            client.getItemModelManager().clearAndUpdate(
                    itemRenderState,
                    stack,
                    ItemDisplayContext.GUI,
                    client.world,
                    entity,
                    entity.getId() + i
            );
            itemRenderState.render(matrices, consumers, FULL_BRIGHT, 0);
            matrices.pop();

            if (stack.isItemBarVisible()) {
                Matrix4f posMatrix = matrices.peek().getPositionMatrix();
                int step = stack.getItemBarStep();
                int barColor = stack.getItemBarColor();
                float barWidth = (step / 13.0F) * ITEM_SIZE;
                float durY = itemY + ITEM_SIZE;

                fillRect(posMatrix, itemX, durY, itemX + ITEM_SIZE, durY + 1, 0xFF000000);
                fillRect(posMatrix, itemX, durY, itemX + barWidth, durY + 1, 0xFF000000 | barColor);
            }

            equipmentBuffer[i] = ItemStack.EMPTY;
        }
    }

    private void fillRect(Matrix4f matrix, float x1, float y1, float x2, float y2, int color) {
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getTextBackgroundSeeThrough());
        consumer.vertex(matrix, x1, y1, 0).color(color).light(0xF0, 0xF0);
        consumer.vertex(matrix, x1, y2, 0).color(color).light(0xF0, 0xF0);
        consumer.vertex(matrix, x2, y2, 0).color(color).light(0xF0, 0xF0);
        consumer.vertex(matrix, x2, y1, 0).color(color).light(0xF0, 0xF0);
    }

    private boolean shouldRender(
            MinecraftClient client,
            WorldRenderContext context,
            Entity entity,
            double cameraX,
            double cameraY,
            double cameraZ,
            double maxDistSq
    ) {
        if (!isHandledEntity(client, entity, maxDistSq)) {
            return false;
        }

        return client.getEntityRenderDispatcher().shouldRender(entity, context.frustum(), cameraX, cameraY, cameraZ);
    }

    private boolean isHandledEntity(MinecraftClient client, Entity entity, double maxDistSq) {
        if (entity == client.player || !entity.isAlive() || entity.isRemoved()) {
            return false;
        }

        EspModule.Category category = EspModule.categoryOf(entity);
        if (category == null || !enabledForCategory(category)) {
            return false;
        }

        return client.player.squaredDistanceTo(entity) <= maxDistSq;
    }

    private boolean enabledForCategory(EspModule.Category category) {
        return switch (category) {
            case PLAYERS -> players.value();
            case MOBS -> mobs.value();
            case ANIMALS -> animals.value();
        };
    }

    public static boolean shouldSuppressLabel(Entity entity) {
        NameTagsModule module = instance;
        if (module == null || !module.enabled()) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || entity == client.player) {
            return false;
        }

        double maxDistSq = module.range.value() * module.range.value();
        return module.isHandledEntity(client, entity, maxDistSq);
    }

    private static int healthColor(float fraction) {
        int r, g;
        if (fraction > 0.5F) {
            float t = (fraction - 0.5F) * 2.0F;
            r = (int) (255 * (1.0F - t));
            g = 255;
        } else {
            float t = fraction * 2.0F;
            r = 255;
            g = (int) (255 * t);
        }
        return 0xFF000000 | (r << 16) | (g << 8);
    }

    private static double lerp(double start, double end, float delta) {
        return start + (end - start) * delta;
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

    private void clearFramebuffer(Framebuffer target) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(target.getColorAttachment(), 0);
        encoder.clearDepthTexture(target.getDepthAttachment(), 1.0D);
    }

    private Framebuffer ensureFramebuffer(MinecraftClient client) {
        int width = client.getWindow().getFramebufferWidth();
        int height = client.getWindow().getFramebufferHeight();

        if (framebuffer == null) {
            framebuffer = new SimpleFramebuffer("Smoke NameTags", width, height, true);
            return framebuffer;
        }

        if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) {
            framebuffer.resize(width, height);
        }
        return framebuffer;
    }

    private record EntitySnapshot(Entity entity, double rx, double ry, double rz) {
    }
}
