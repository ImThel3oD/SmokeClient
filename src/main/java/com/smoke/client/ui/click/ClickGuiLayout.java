package com.smoke.client.ui.click;

record ClickGuiLayout(
        Rect window,
        Rect header,
        Rect tabs,
        Rect search,
        Rect list,
        Rect footer
) {
    static ClickGuiLayout create(int screenWidth, int screenHeight) {
        int margin = Math.max(10, Math.min(ClickGuiPalette.OUTER_MARGIN, Math.min(screenWidth, screenHeight) / 10));
        int availableWidth = Math.max(420, screenWidth - (margin * 2));
        int availableHeight = Math.max(260, screenHeight - (margin * 2));
        int windowWidth = Math.min(760, availableWidth);
        int windowHeight = Math.min(454, availableHeight);
        int windowX = (screenWidth - windowWidth) / 2;
        int windowY = (screenHeight - windowHeight) / 2;

        Rect window = new Rect(windowX, windowY, windowWidth, windowHeight);
        int innerX = windowX + ClickGuiPalette.INNER_PADDING;
        int innerWidth = windowWidth - (ClickGuiPalette.INNER_PADDING * 2);

        Rect header = new Rect(innerX, windowY + 14, innerWidth, 22);
        Rect tabs = new Rect(innerX, header.bottom() + 14, innerWidth, ClickGuiPalette.TAB_HEIGHT);
        Rect search = new Rect(innerX, tabs.bottom() + 12, innerWidth, ClickGuiPalette.SEARCH_HEIGHT);
        Rect footer = new Rect(innerX, window.bottom() - ClickGuiPalette.INNER_PADDING - ClickGuiPalette.FOOTER_HEIGHT, innerWidth, ClickGuiPalette.FOOTER_HEIGHT);
        Rect list = new Rect(
                innerX,
                search.bottom() + 12,
                innerWidth,
                footer.y() - (search.bottom() + 20)
        );

        return new ClickGuiLayout(window, header, tabs, search, list, footer);
    }

    ClickGuiLayout translate(int dx, int dy) {
        return new ClickGuiLayout(
                window.translate(dx, dy),
                header.translate(dx, dy),
                tabs.translate(dx, dy),
                search.translate(dx, dy),
                list.translate(dx, dy),
                footer.translate(dx, dy)
        );
    }

    record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        int centerX() {
            return x + (width / 2);
        }

        int centerY() {
            return y + (height / 2);
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }

        Rect translate(int dx, int dy) {
            return new Rect(x + dx, y + dy, width, height);
        }
    }
}
