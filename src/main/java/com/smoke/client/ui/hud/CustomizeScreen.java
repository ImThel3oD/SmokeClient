package com.smoke.client.ui.hud;

import com.smoke.client.bootstrap.ClientRuntime;
import com.smoke.client.feature.module.render.TargetHudModule;
import com.smoke.client.input.BlocksModuleKeybindsScreen;
import com.smoke.client.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class CustomizeScreen extends Screen implements BlocksModuleKeybindsScreen {
    private final ClientRuntime runtime;
    private final int closeKey;
    private Draggable dragging;
    private int dragX;
    private int dragY;

    public CustomizeScreen(ClientRuntime runtime, int closeKey) {
        super(Text.literal("Customize HUD"));
        this.runtime = runtime;
        this.closeKey = closeKey;
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        drawContext.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, "Drag HUD elements", 6, 6, 0xFFFFFFFF);
        drawContext.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, "Press Esc to save and close", 6, 16, 0xB0FFFFFF);
        for (Module module : runtime.moduleManager().all()) {
            if (!(module instanceof Draggable draggable)) {
                continue;
            }
            if (module instanceof TargetHudModule targetHud) {
                targetHud.renderPreview(drawContext);
            }
            int x = draggable.getPosX();
            int y = draggable.getPosY();
            int color = draggable == dragging ? 0xCCFFFFFF : 0x66FFFFFF;
            drawContext.fill(x, y, x + draggable.getWidth(), y + 1, color);
            drawContext.fill(x, y + draggable.getHeight() - 1, x + draggable.getWidth(), y + draggable.getHeight(), color);
            drawContext.fill(x, y, x + 1, y + draggable.getHeight(), color);
            drawContext.fill(x + draggable.getWidth() - 1, y, x + draggable.getWidth(), y + draggable.getHeight(), color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (Module module : runtime.moduleManager().all()) {
            if (module instanceof Draggable draggable
                    && mouseX >= draggable.getPosX() && mouseX < draggable.getPosX() + draggable.getWidth()
                    && mouseY >= draggable.getPosY() && mouseY < draggable.getPosY() + draggable.getHeight()) {
                dragging = draggable;
                dragX = (int) mouseX - draggable.getPosX();
                dragY = (int) mouseY - draggable.getPosY();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging != null && button == 0) {
            dragging.setPosX(Math.clamp((int) mouseX - dragX, 0, Math.max(0, width - dragging.getWidth())));
            dragging.setPosY(Math.clamp((int) mouseY - dragY, 0, Math.max(0, height - dragging.getHeight())));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || closeKey > 0 && keyCode == closeKey) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        dragging = null;
        runtime.configService().save(runtime.moduleManager());
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean allowClientHotkey(int keyCode) {
        return keyCode != GLFW.GLFW_KEY_RIGHT_SHIFT;
    }
}
