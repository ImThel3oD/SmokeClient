package com.smoke.client.ui.click;

import net.minecraft.util.math.MathHelper;

record ClickGuiLayout(
        Rect window,
        Rect sidebar,
        Rect content,
        Rect search,
        Rect list,
        int sidebarHeaderBottom,
        int contentHeaderY
) {
    static ClickGuiLayout create(int screenWidth, int screenHeight) {
        int margin = Math.max(6, Math.min(ClickGuiPalette.OUTER_MARGIN, Math.min(screenWidth, screenHeight) / 12));
        int availableWidth = Math.max(220, screenWidth - (margin * 2));
        int availableHeight = Math.max(140, screenHeight - (margin * 2));
        int windowWidth = Math.min(520, availableWidth);
        int windowHeight = Math.min(360, availableHeight);
        int windowX = (screenWidth - windowWidth) / 2;
        int windowY = (screenHeight - windowHeight) / 2;

        Rect window = new Rect(windowX, windowY, windowWidth, windowHeight);
        int sidebarWidth = MathHelper.clamp(windowWidth / 5, 72, 96);
        Rect sidebar = new Rect(
                windowX + ClickGuiPalette.INNER_PADDING,
                windowY + ClickGuiPalette.INNER_PADDING,
                sidebarWidth,
                windowHeight - (ClickGuiPalette.INNER_PADDING * 2)
        );

        int contentX = sidebar.right() + ClickGuiPalette.CONTENT_GAP;
        Rect content = new Rect(
                contentX,
                sidebar.y(),
                window.right() - ClickGuiPalette.INNER_PADDING - contentX,
                sidebar.height()
        );

        int searchY = content.y() + 18;
        Rect search = new Rect(content.x(), searchY, content.width(), ClickGuiPalette.SEARCH_HEIGHT + 8);
        Rect list = new Rect(
                content.x(),
                search.bottom() + 6,
                content.width(),
                content.bottom() - (search.bottom() + 6)
        );

        return new ClickGuiLayout(window, sidebar, content, search, list, sidebar.y() + 28, content.y() + 2);
    }

    record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }
    }
}
