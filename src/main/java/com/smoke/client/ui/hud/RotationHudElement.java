package com.smoke.client.ui.hud;

import com.smoke.client.bootstrap.ClientRuntime;
import com.smoke.client.rotation.RotationFrame;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public final class RotationHudElement implements HudElement {
    private static float cleanZero(float value) {
        return value == 0.0F ? 0.0F : value;
    }

    @Override
    public String id() {
        return "rotation";
    }

    @Override
    public void render(DrawContext drawContext, RenderTickCounter tickCounter, ClientRuntime runtime) {
        RotationFrame frame = runtime.rotationService().currentFrame();
        if (!frame.active()) {
            return;
        }

        String line = String.format(
                "Rot[%s] pkt=%.1f/%.1f tgt=%.1f/%.1f",
                frame.ownerId(),
                cleanZero(frame.packetYaw()),
                cleanZero(frame.packetPitch()),
                cleanZero(frame.targetYaw()),
                cleanZero(frame.targetPitch())
        );

        int y = HudLayout.belowInfo(runtime);
        drawContext.drawText(runtime.theme().textRenderer(), line, HudLayout.LEFT_X, y, runtime.theme().accentColor(), true);
    }
}
