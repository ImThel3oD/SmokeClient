package com.smoke.client.ui.click;

import com.smoke.client.module.ModuleCategory;

final class ClickGuiPalette {
    static final int OVERLAY_TOP = 0xAD000000;
    static final int OVERLAY_BOTTOM = 0xD8000000;
    static final int WINDOW_TOP = 0xF0101010;
    static final int WINDOW_BOTTOM = 0xF0060606;
    static final int SIDEBAR_TOP = 0xFF101010;
    static final int SIDEBAR_BOTTOM = 0xFF090909;
    static final int CONTENT_TOP = 0xFF0D0D0D;
    static final int CONTENT_BOTTOM = 0xFF070707;
    static final int SURFACE_TOP = 0xFF141414;
    static final int SURFACE_BOTTOM = 0xFF0D0D0D;
    static final int SURFACE_ALT_TOP = 0xFF191919;
    static final int SURFACE_ALT_BOTTOM = 0xFF101010;
    static final int SURFACE_ACTIVE_TOP = 0xFF23170A;
    static final int SURFACE_ACTIVE_BOTTOM = 0xFF17100A;
    static final int BORDER = 0xFF262626;
    static final int BORDER_SOFT = 0xAA202020;
    static final int INNER_HIGHLIGHT = 0x10FFFFFF;
    static final int ACCENT = 0xFFFF8A1A;
    static final int ACCENT_SOFT = 0x38FF8A1A;
    static final int TEXT_PRIMARY = 0xFFF4F4F4;
    static final int TEXT_SECONDARY = 0xFFD0D0D0;
    static final int TEXT_MUTED = 0xFF919191;
    static final int TEXT_DISABLED = 0xFF666666;
    static final int TRACK = 0xFF050505;
    static final int TRACK_FILL = ACCENT;
    static final int SWITCH_OFF = 0xFF2A2A2A;
    static final int SWITCH_KNOB = 0xFFF6E6D6;
    static final int SHADOW = 0xD8000000;
    static final int DANGER_MUTED = 0xFF9E6D6D;

    static final int OUTER_MARGIN = 24;
    static final int INNER_PADDING = 18;
    static final int CONTENT_GAP = 14;
    static final int SECTION_GAP = 10;
    static final int CATEGORY_ROW_HEIGHT = 28;
    static final int TAB_HEIGHT = 28;
    static final int SEARCH_HEIGHT = 30;
    static final int FOOTER_HEIGHT = 12;
    static final int MODULE_ROW_HEIGHT = 46;
    static final int SETTING_ROW_HEIGHT = 28;
    static final int NUMBER_ROW_HEIGHT = 34;
    static final int COLOR_CHANNEL_HEIGHT = 28;
    static final int BADGE_HEIGHT = 16;
    static final int TOGGLE_WIDTH = 38;
    static final int TOGGLE_HEIGHT = 18;
    static final int WINDOW_RADIUS = 12;
    static final int PANEL_RADIUS = 8;
    static final int BADGE_RADIUS = 5;
    static final int CONTROL_RADIUS = 6;

    private ClickGuiPalette() {
    }

    static int categoryAccent(ModuleCategory category) {
        return ACCENT;
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }
}
