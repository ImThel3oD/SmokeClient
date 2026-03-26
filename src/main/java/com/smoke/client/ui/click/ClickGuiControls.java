package com.smoke.client.ui.click;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

final class ClickGuiControls {
    private ClickGuiControls() {
    }

    static ClickGuiLayout.Rect actionRect(ClickGuiLayout.Rect rowRect) {
        int buttonWidth = Math.min(112, Math.max(84, rowRect.width() / 2));
        int buttonHeight = Math.max(18, ClickGuiPalette.SETTING_ROW_HEIGHT - 8);
        return new ClickGuiLayout.Rect(
                rowRect.right() - buttonWidth - 8,
                rowRect.y() + Math.max(2, (rowRect.height() - buttonHeight) / 2),
                buttonWidth,
                buttonHeight
        );
    }

    static ClickGuiLayout.Rect toggleRect(ClickGuiLayout.Rect rowRect) {
        return new ClickGuiLayout.Rect(
                rowRect.right() - ClickGuiPalette.TOGGLE_WIDTH - 8,
                rowRect.y() + Math.max(2, (rowRect.height() - ClickGuiPalette.TOGGLE_HEIGHT) / 2),
                ClickGuiPalette.TOGGLE_WIDTH,
                ClickGuiPalette.TOGGLE_HEIGHT
        );
    }

    static ClickGuiLayout.Rect sliderRect(ClickGuiLayout.Rect rowRect) {
        return new ClickGuiLayout.Rect(rowRect.x() + 10, rowRect.bottom() - 10, rowRect.width() - 20, 6);
    }

    static ClickGuiLayout.Rect colorSliderRect(ClickGuiLayout.Rect rowRect) {
        return sliderRect(rowRect);
    }

    static void renderToggle(DrawContext drawContext, ClickGuiLayout.Rect rect, boolean enabled) {
        int trackTop = enabled
                ? ClickGuiPaint.mix(ClickGuiPalette.ACCENT, 0xFFFFFFFF, 0.10F)
                : ClickGuiPalette.SWITCH_OFF;
        int trackBottom = enabled ? ClickGuiPalette.ACCENT : ClickGuiPalette.SWITCH_OFF;
        ClickGuiPaint.drawPanel(
                drawContext,
                rect,
                trackTop,
                trackBottom,
                enabled ? ClickGuiPalette.withAlpha(ClickGuiPalette.ACCENT, 180) : ClickGuiPalette.BORDER_SOFT,
                ClickGuiPalette.withAlpha(ClickGuiPalette.TEXT_PRIMARY, 18)
        );
        int knobWidth = 14;
        int knobX = enabled ? rect.right() - knobWidth - 2 : rect.x() + 2;
        drawContext.fill(knobX, rect.y() + 2, knobX + knobWidth, rect.bottom() - 2, ClickGuiPalette.SWITCH_KNOB);
        drawContext.fill(knobX, rect.y() + 2, knobX + knobWidth, rect.y() + 3, ClickGuiPalette.withAlpha(0xFFFFFFFF, 54));
    }

    static void renderSlider(DrawContext drawContext, ClickGuiLayout.Rect rect, double normalized, int fillColor) {
        drawContext.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), ClickGuiPalette.TRACK);
        int fillWidth = (int) Math.round(rect.width() * MathHelper.clamp((float) normalized, 0.0F, 1.0F));
        drawContext.fill(rect.x(), rect.y(), rect.x() + fillWidth, rect.bottom(), fillColor);
        renderSliderHandle(drawContext, rect, rect.x() + fillWidth);
    }

    static void renderRangeSlider(DrawContext drawContext, ClickGuiLayout.Rect rect, double lowerNormalized, double upperNormalized, int fillColor) {
        drawContext.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), ClickGuiPalette.TRACK);
        int lowerX = sliderPosition(rect, lowerNormalized);
        int upperX = sliderPosition(rect, upperNormalized);
        if (lowerX > upperX) {
            int swap = lowerX;
            lowerX = upperX;
            upperX = swap;
        }
        drawContext.fill(lowerX, rect.y(), Math.max(lowerX + 1, upperX), rect.bottom(), fillColor);
        renderSliderHandle(drawContext, rect, lowerX);
        renderSliderHandle(drawContext, rect, upperX);
    }

    static int sliderPosition(ClickGuiLayout.Rect rect, double normalized) {
        return MathHelper.clamp(
                rect.x() + (int) Math.round(rect.width() * MathHelper.clamp((float) normalized, 0.0F, 1.0F)),
                rect.x(),
                rect.right()
        );
    }

    private static void renderSliderHandle(DrawContext drawContext, ClickGuiLayout.Rect rect, int centerX) {
        int knobX = MathHelper.clamp(centerX - 4, rect.x(), rect.right() - 8);
        drawContext.fill(knobX, rect.y() - 2, knobX + 8, rect.bottom() + 2, ClickGuiPalette.SWITCH_KNOB);
    }
}
