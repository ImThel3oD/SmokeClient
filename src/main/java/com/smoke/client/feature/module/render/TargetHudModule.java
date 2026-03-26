package com.smoke.client.feature.module.render;

import com.smoke.client.event.Subscribe;
import com.smoke.client.event.events.HudRenderEvent;
import com.smoke.client.feature.module.combat.KillAuraModule;
import com.smoke.client.feature.module.combat.SilentAuraModule;
import com.smoke.client.feature.module.combat.TriggerBotModule;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.module.ModuleContext;
import com.smoke.client.setting.NumberSetting;
import com.smoke.client.ui.hud.Draggable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class TargetHudModule extends Module implements Draggable {
    private static final int WIDTH = 124;
    private static final int HEIGHT = 48;
    private static final int ICON = 10;
    private static final float ICON_SCALE = 0.625F;
    private final NumberSetting posX = addSetting(new NumberSetting("pos_x", "Pos X", "TargetHUD X position.", 6.0D, 0.0D, 2000.0D, 1.0D));
    private final NumberSetting posY = addSetting(new NumberSetting("pos_y", "Pos Y", "TargetHUD Y position.", -1.0D, -1.0D, 2000.0D, 1.0D));
    private Entity lastTarget;

    public TargetHudModule(ModuleContext context) {
        super(context, "target_hud", "TargetHUD", "Shows the current combat target.", ModuleCategory.RENDER, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Subscribe
    private void onHudRender(HudRenderEvent event) {
        Entity target = resolveTarget();
        if (target != null) {
            lastTarget = target;
            render(event.drawContext(), target);
        }
    }

    public void renderPreview(DrawContext drawContext) {
        Entity preview = lastTarget != null ? lastTarget : MinecraftClient.getInstance().player;
        if (preview != null) {
            render(drawContext, preview);
        }
    }

    private void render(DrawContext drawContext, Entity target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.textRenderer == null) {
            return;
        }
        LivingEntity living = target instanceof LivingEntity entity ? entity : null;
        int x = Math.clamp(getPosX(), 0, Math.max(0, drawContext.getScaledWindowWidth() - WIDTH));
        int y = Math.clamp(getPosY(), 0, Math.max(0, drawContext.getScaledWindowHeight() - HEIGHT));
        String healthText = String.format(Locale.ROOT, "%.1f", living == null ? 18.0F : living.getHealth());
        int barX = x + 26;
        int textX = x + WIDTH - 6 - client.textRenderer.getWidth(healthText);
        int barWidth = Math.max(20, textX - barX - 4);
        drawContext.fill(x, y, x + WIDTH, y + HEIGHT, 0x90000000);
        drawHead(drawContext, x + 6, y + 6, target);
        drawContext.drawTextWithShadow(client.textRenderer, target.getDisplayName().getString(), x + 26, y + 6, 0xFFFFFFFF);
        drawItems(drawContext, living, x + 26, y + 18);
        drawContext.fill(barX, y + 37, barX + barWidth, y + 40, 0xFF303030);
        if (living != null && living.getMaxHealth() > 0.0F) {
            float ratio = Math.clamp(living.getHealth() / living.getMaxHealth(), 0.0F, 1.0F);
            drawContext.fill(barX, y + 37, barX + Math.round(barWidth * ratio), y + 40, healthColor(ratio));
        }
        drawContext.drawTextWithShadow(client.textRenderer, healthText, textX, y + 33, 0xFFFFFFFF);
    }

    private void drawHead(DrawContext drawContext, int x, int y, Entity target) {
        MinecraftClient client = MinecraftClient.getInstance();
        AbstractClientPlayerEntity player = target instanceof AbstractClientPlayerEntity entity
                ? entity
                : target == client.player ? client.player : null;
        if (player == null) {
            drawContext.fill(x, y, x + 16, y + 16, 0x90404040);
            return;
        }
        var skin = player.getSkinTextures().texture();
        drawContext.drawTexture(RenderPipelines.GUI_TEXTURED, skin, x, y, 8.0F, 8.0F, 16, 16, 8, 8, 64, 64);
        drawContext.drawTexture(RenderPipelines.GUI_TEXTURED, skin, x, y, 40.0F, 8.0F, 16, 16, 8, 8, 64, 64);
    }

    private void drawItems(DrawContext drawContext, LivingEntity living, int x, int y) {
        ItemStack[] stacks = living == null
                ? new ItemStack[]{ItemStack.EMPTY}
                : new ItemStack[]{living.getEquippedStack(EquipmentSlot.HEAD), living.getEquippedStack(EquipmentSlot.CHEST), living.getEquippedStack(EquipmentSlot.LEGS), living.getEquippedStack(EquipmentSlot.FEET), living.getMainHandStack(), living.getOffHandStack()};
        int index = 0;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            int itemX = x + index++ * (ICON + 2);
            Matrix3x2fStack matrices = drawContext.getMatrices();
            matrices.pushMatrix();
            matrices.translate(itemX, y);
            matrices.scale(ICON_SCALE, ICON_SCALE);
            drawContext.drawItem(stack, 0, 0);
            matrices.popMatrix();
            if (stack.isItemBarVisible()) {
                int width = Math.max(1, Math.round((stack.getItemBarStep() / 13.0F) * ICON));
                drawContext.fill(itemX, y + ICON + 1, itemX + ICON, y + ICON + 2, 0xFF000000);
                drawContext.fill(itemX, y + ICON + 1, itemX + width, y + ICON + 2, 0xFF000000 | stack.getItemBarColor());
            }
        }
    }

    private Entity resolveTarget() {
        KillAuraModule killAura = context().modules().getByType(KillAuraModule.class).orElse(null);
        if (killAura != null && killAura.enabled() && killAura.getTarget() != null) return killAura.getTarget();
        SilentAuraModule silentAura = context().modules().getByType(SilentAuraModule.class).orElse(null);
        if (silentAura != null && silentAura.enabled() && silentAura.getTarget() != null) return silentAura.getTarget();
        TriggerBotModule triggerBot = context().modules().getByType(TriggerBotModule.class).orElse(null);
        return triggerBot != null && triggerBot.enabled() ? triggerBot.getTarget() : null;
    }

    private static int healthColor(float ratio) {
        int red = ratio > 0.5F ? (int) (255 * (1.0F - (ratio - 0.5F) * 2.0F)) : 255;
        int green = ratio > 0.5F ? 255 : (int) (255 * (ratio * 2.0F));
        return 0xFF000000 | red << 16 | green << 8;
    }

    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }
    @Override public int getPosX() { return posX.value().intValue(); }
    @Override public int getPosY() {
        MinecraftClient client = MinecraftClient.getInstance();
        return posY.value() < 0.0D && client != null && client.getWindow() != null
                ? Math.max(6, client.getWindow().getScaledHeight() - HEIGHT - 6)
                : Math.max(0, posY.value().intValue());
    }
    @Override public void setPosX(int x) { posX.setValue((double) x); }
    @Override public void setPosY(int y) { posY.setValue((double) y); }
}
