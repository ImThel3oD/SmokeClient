package com.smoke.client.ui.hud;

import com.smoke.client.bootstrap.ClientRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.hit.HitResult;

import java.util.Locale;

public final class InfoHudElement implements HudElement {
    @Override
    public String id() {
        return "info";
    }

    @Override
    public void render(DrawContext drawContext, RenderTickCounter tickCounter, ClientRuntime runtime) {
        if (!HudLayout.isInfoEnabled(runtime)) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }

        int fps = client.getCurrentFps();

        String rangeText = "-";
        if (client.crosshairTarget != null && client.crosshairTarget.getType() != HitResult.Type.MISS) {
            double range = client.player.getEyePos().distanceTo(client.crosshairTarget.getPos());
            rangeText = String.format(Locale.ROOT, "%.2f", range);
        }

        float yaw = client.player.getYaw();
        float pitch = client.player.getPitch();
        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();

        String[] lines = new String[]{
                String.format(Locale.ROOT, "[ FPS: %d ]", fps),
                String.format(Locale.ROOT, "[ Range: %s ]", rangeText),
                String.format(Locale.ROOT, "[ Yaw: %.1f ]", yaw),
                String.format(Locale.ROOT, "[ Pitch: %.1f ]", pitch),
                String.format(Locale.ROOT, "[ X: %.2f ]", x),
                String.format(Locale.ROOT, "[ Y: %.2f ]", y),
                String.format(Locale.ROOT, "[ Z: %.2f ]", z)
        };

        int drawY = HudLayout.belowArrayList(runtime);
        for (String line : lines) {
            drawContext.drawText(runtime.theme().textRenderer(), line, HudLayout.LEFT_X, drawY, runtime.theme().accentColor(), true);
            drawY += HudLayout.LINE_HEIGHT;
        }
    }
}

