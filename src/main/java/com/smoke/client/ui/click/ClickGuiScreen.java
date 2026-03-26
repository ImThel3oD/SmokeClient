package com.smoke.client.ui.click;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.smoke.client.bootstrap.ClientRuntime;
import com.smoke.client.input.BlocksModuleKeybindsScreen;
import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleCategory;
import com.smoke.client.setting.BoolSetting;
import com.smoke.client.setting.ColorSetting;
import com.smoke.client.setting.EnumSetting;
import com.smoke.client.setting.KeyBindSetting;
import com.smoke.client.setting.NumberSetting;
import com.smoke.client.setting.Setting;
import com.smoke.client.ui.font.SmokeFonts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ClickGuiScreen extends Screen implements BlocksModuleKeybindsScreen {
    private static final String UI_SECTION = "click_gui";
    private static final String FILTER_KEY = "selected_filter";
    private static final String FILTER_ALL = "all";
    private static final int MODULE_SUFFIX_GAP = 6;
    private static final int SEARCH_MAX_LENGTH = 48;
    private static final int WINDOW_ANIMATION_OFFSET = 18;

    private final ClientRuntime runtime;
    private final List<FilterChip> filterChips = new ArrayList<>();
    private final Map<String, Float> moduleHoverAnimations = new HashMap<>();
    private final Map<String, Float> moduleEnabledAnimations = new HashMap<>();
    private final Map<String, Float> moduleExpandAnimations = new HashMap<>();
    private final Map<String, Float> filterHoverAnimations = new HashMap<>();
    private final Map<String, Float> filterSelectAnimations = new HashMap<>();

    private ClickGuiLayout layout;
    private ModuleCategory selectedCategory = ModuleCategory.COMBAT;
    private boolean showAllCategories = true;
    private String expandedModuleId;
    private String openColorSettingPath;
    private String pendingKeybindPath;
    private DragTarget activeDrag;
    private String searchQuery = "";
    private int searchCaret;
    private boolean searchFocused = true;
    private boolean searchSelectAll;
    private double listScroll;
    private double renderScroll;
    private double maxScroll;
    private float windowProgress;
    private float searchFocusAnimation;
    private boolean closing;
    private long lastFrameNanos;

    public ClickGuiScreen(ClientRuntime runtime) {
        super(Text.literal("Smoke Click GUI"));
        this.runtime = runtime;
        restoreFilter();
    }

    @Override
    protected void init() {
        super.init();
        searchCaret = Math.min(searchCaret, searchQuery.length());
        lastFrameNanos = 0L;
        windowProgress = 0.0F;
        searchFocusAnimation = searchFocused ? 1.0F : 0.0F;
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        float frameDelta = frameDeltaSeconds();
        windowProgress = animateValue(windowProgress, closing ? 0.0F : 1.0F, frameDelta, 10.0F);
        if (closing && windowProgress <= 0.02F) {
            finishClose();
            return;
        }

        ClickGuiLayout baseLayout = ClickGuiLayout.create(width, height);
        layout = baseLayout.translate(0, Math.round((1.0F - easeOut(windowProgress)) * WINDOW_ANIMATION_OFFSET));
        buildFilterChips(layout);

        List<Module> modules = filteredModules();
        if (expandedModuleId != null && modules.stream().noneMatch(module -> module.id().equals(expandedModuleId))) {
            expandedModuleId = null;
            openColorSettingPath = null;
            pendingKeybindPath = null;
            activeDrag = null;
        }

        updateAnimations(mouseX, mouseY, frameDelta, modules);
        int contentWidth = layout.list().width() - 12 - scrollbarWidth(modules);
        int totalHeight = measureModuleListHeight(modules, contentWidth);
        maxScroll = Math.max(0.0D, totalHeight - (layout.list().height() - 10.0D));
        listScroll = MathHelper.clamp((float) listScroll, 0.0F, (float) maxScroll);
        renderScroll = animateDouble(renderScroll, listScroll, frameDelta, 14.0F);
        searchFocusAnimation = animateValue(searchFocusAnimation, searchFocused ? 1.0F : 0.0F, frameDelta, 14.0F);

        renderBackdrop(drawContext);
        renderHeader(drawContext, modules);
        renderTabs(drawContext);
        renderSearch(drawContext);
        renderModuleList(drawContext, modules, contentWidth);
        renderFooter(drawContext);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || layout == null || closing || windowProgress < 0.94F) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (layout.search().contains(mouseX, mouseY)) {
            focusSearchAt(mouseX);
            return true;
        }
        if (handleCategoryClick(mouseX, mouseY)) {
            searchFocused = false;
            searchSelectAll = false;
            return true;
        }
        if (handleModuleClick(mouseX, mouseY)) {
            searchFocused = false;
            searchSelectAll = false;
            return true;
        }

        searchFocused = false;
        searchSelectAll = false;
        openColorSettingPath = null;
        pendingKeybindPath = null;
        activeDrag = null;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (activeDrag != null && button == 0 && !closing) {
            activeDrag.update(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            activeDrag = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (layout != null && layout.list().contains(mouseX, mouseY) && maxScroll > 0.0D && !closing) {
            listScroll = MathHelper.clamp((float) (listScroll - (verticalAmount * 26.0D)), 0.0F, (float) maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (pendingKeybindPath != null) {
            return true;
        }
        if (searchFocused && !closing && chr >= 32 && chr != 127) {
            replaceSelectionIfNeeded();
            if (searchQuery.length() < SEARCH_MAX_LENGTH) {
                searchQuery = searchQuery.substring(0, searchCaret) + chr + searchQuery.substring(searchCaret);
                searchCaret++;
                listScroll = 0.0D;
                clearInvalidExpandedModule();
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pendingKeybindPath != null) {
            Optional<Setting<?>> pendingSetting = resolveSetting(pendingKeybindPath);
            if (pendingSetting.filter(KeyBindSetting.class::isInstance).isPresent()) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    pendingKeybindPath = null;
                    return true;
                }
                int assignedKey = keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE
                        ? GLFW.GLFW_KEY_UNKNOWN
                        : keyCode;
                ((KeyBindSetting) pendingSetting.get()).setValue(assignedKey);
                pendingKeybindPath = null;
                return true;
            }
            pendingKeybindPath = null;
        }

        if (searchFocused && handleSearchKeyPress(keyCode)) {
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (searchFocused) {
                searchFocused = false;
                searchSelectAll = false;
            } else {
                requestClose();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        requestClose();
    }

    public void requestClose() {
        if (!closing) {
            closing = true;
            searchFocused = false;
            searchSelectAll = false;
            activeDrag = null;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean allowClientHotkey(int keyCode) {
        return pendingKeybindPath == null;
    }

    private void renderBackdrop(DrawContext drawContext) {
        float eased = easeOut(windowProgress);
        drawContext.fillGradient(
                0,
                0,
                width,
                height,
                scaleAlpha(ClickGuiPalette.OVERLAY_TOP, eased),
                scaleAlpha(ClickGuiPalette.OVERLAY_BOTTOM, eased)
        );
        ClickGuiPaint.drawBackdropBlur(drawContext, layout.window(), eased * 0.9F);
        ClickGuiPaint.drawShadow(drawContext, layout.window(), 14, scaleAlpha(ClickGuiPalette.SHADOW, eased));
        ClickGuiPaint.drawPanel(
                drawContext,
                layout.window(),
                ClickGuiPalette.WINDOW_TOP,
                ClickGuiPalette.WINDOW_BOTTOM,
                ClickGuiPalette.BORDER,
                ClickGuiPalette.INNER_HIGHLIGHT,
                ClickGuiPalette.WINDOW_RADIUS
        );
        drawContext.fill(layout.window().x() + 18, layout.window().y() + 11, layout.window().right() - 18, layout.window().y() + 13, ClickGuiPalette.ACCENT);
    }

    private void renderHeader(DrawContext drawContext, List<Module> modules) {
        drawStyledText(drawContext, "Smoke", layout.header().x(), layout.header().y() - 1, ClickGuiPalette.ACCENT, ClickFont.TITLE);
        String results = modules.size() + (modules.size() == 1 ? " result" : " results");
        drawStyledText(
                drawContext,
                results,
                layout.header().right() - textWidth(results, ClickFont.SMALL),
                layout.header().y() + 5,
                ClickGuiPalette.TEXT_MUTED,
                ClickFont.SMALL
        );
    }

    private void renderTabs(DrawContext drawContext) {
        for (FilterChip chip : filterChips) {
            float hover = filterHoverAnimations.getOrDefault(chip.filterId(), 0.0F);
            float selected = filterSelectAnimations.getOrDefault(chip.filterId(), 0.0F);
            int fillTop = ClickGuiPaint.mix(ClickGuiPalette.SURFACE_ALT_TOP, ClickGuiPalette.SURFACE_ACTIVE_TOP, Math.max(hover * 0.55F, selected));
            int fillBottom = ClickGuiPaint.mix(ClickGuiPalette.SURFACE_ALT_BOTTOM, ClickGuiPalette.SURFACE_ACTIVE_BOTTOM, Math.max(hover * 0.45F, selected));
            int border = ClickGuiPaint.mix(ClickGuiPalette.BORDER_SOFT, ClickGuiPalette.withAlpha(ClickGuiPalette.ACCENT, 200), Math.max(selected, hover * 0.4F));
            int text = selected >= 0.5F
                    ? ClickGuiPalette.ACCENT
                    : hover > 0.0F ? ClickGuiPalette.TEXT_PRIMARY : ClickGuiPalette.TEXT_SECONDARY;

            if (selected > 0.08F) {
                ClickGuiPaint.drawGlow(drawContext, chip.rect(), 6, ClickGuiPalette.withAlpha(ClickGuiPalette.ACCENT, Math.round(56 * selected)));
            }
            ClickGuiPaint.drawPanel(drawContext, chip.rect(), fillTop, fillBottom, border, ClickGuiPalette.INNER_HIGHLIGHT, ClickGuiPalette.CONTROL_RADIUS);
            drawStyledText(
                    drawContext,
                    chip.label(),
                    chip.rect().x() + (chip.rect().width() - textWidth(chip.label(), ClickFont.BODY)) / 2,
                    chip.rect().y() + 7,
                    text,
                    ClickFont.BODY
            );
        }
    }

    private void renderSearch(DrawContext drawContext) {
        ClickGuiLayout.Rect searchRect = layout.search();
        if (searchFocusAnimation > 0.08F) {
            ClickGuiPaint.drawGlow(drawContext, searchRect, 6, ClickGuiPalette.withAlpha(ClickGuiPalette.ACCENT, Math.round(62 * searchFocusAnimation)));
        }
        ClickGuiPaint.drawPanel(
                drawContext,
                searchRect,
                ClickGuiPaint.mix(ClickGuiPalette.SURFACE_TOP, ClickGuiPalette.SURFACE_ALT_TOP, searchFocusAnimation * 0.4F),
                ClickGuiPaint.mix(ClickGuiPalette.SURFACE_BOTTOM, ClickGuiPalette.SURFACE_ALT_BOTTOM, searchFocusAnimation * 0.4F),
                ClickGuiPaint.mix(ClickGuiPalette.BORDER_SOFT, ClickGuiPalette.withAlpha(ClickGuiPalette.ACCENT, 180), searchFocusAnimation),
                ClickGuiPalette.INNER_HIGHLIGHT,
                ClickGuiPalette.PANEL_RADIUS
        );

        ClickGuiLayout.Rect textRect = new ClickGuiLayout.Rect(searchRect.x() + 14, searchRect.y() + 7, searchRect.width() - 28, searchRect.height() - 14);
        SearchView searchView = computeSearchView(textRect.width());
        drawContext.enableScissor(textRect.x(), textRect.y(), textRect.right(), textRect.bottom());

        if (searchQuery.isEmpty()) {
            drawStyledText(drawContext, "Search modules", textRect.x(), textRect.y() + 1, ClickGuiPalette.TEXT_DISABLED, ClickFont.BODY);
        } else {
            if (searchSelectAll) {
                drawContext.fill(
                        textRect.x(),
                        textRect.y() + 1,
                        textRect.x() + textWidth(searchView.visibleText(), ClickFont.BODY) + 2,
                        textRect.bottom() - 1,
                        ClickGuiPalette.withAlpha(ClickGuiPalette.ACCENT, 42)
                );
            }
            drawStyledText(drawContext, searchView.visibleText(), textRect.x(), textRect.y() + 1, ClickGuiPalette.TEXT_PRIMARY, ClickFont.BODY);
            if (searchFocused && !searchSelectAll && caretVisible()) {
                int caretX = textRect.x() + searchView.caretOffset();
                drawContext.fill(caretX, textRect.y() - 1, caretX + 1, textRect.bottom() + 1, ClickGuiPalette.ACCENT);
            }
        }
        drawContext.disableScissor();
    }

    private void renderFooter(DrawContext drawContext) {
        drawStyledText(drawContext, "Right Shift to close", layout.footer().x(), layout.footer().y(), ClickGuiPalette.TEXT_DISABLED, ClickFont.SMALL);
    }

    private void renderModuleList(DrawContext drawContext, List<Module> modules, int contentWidth) {
        if (modules.isEmpty()) {
            String label = isSearchActive() ? "No modules match that search." : "No modules in this category yet.";
            drawStyledText(
                    drawContext,
                    label,
                    layout.list().x() + (layout.list().width() - textWidth(label, ClickFont.SMALL)) / 2,
                    layout.list().y() + (layout.list().height() / 2) - 4,
                    ClickGuiPalette.TEXT_MUTED,
                    ClickFont.SMALL
            );
            return;
        }

        drawContext.enableScissor(layout.list().x(), layout.list().y(), layout.list().right(), layout.list().bottom());
        int rowX = layout.list().x() + 2;
        int rowY = layout.list().y() + 2 - (int) Math.round(renderScroll);

        for (Module module : modules) {
            float hover = moduleHoverAnimations.getOrDefault(module.id(), 0.0F);
            float enabled = moduleEnabledAnimations.getOrDefault(module.id(), 0.0F);
            float expand = moduleExpandAnimations.getOrDefault(module.id(), 0.0F);

            ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(rowX, rowY, contentWidth, ClickGuiPalette.MODULE_ROW_HEIGHT);
            int topFill = ClickGuiPaint.mix(ClickGuiPalette.SURFACE_TOP, ClickGuiPalette.SURFACE_ALT_TOP, Math.max(hover * 0.55F, expand * 0.5F));
            int bottomFill = ClickGuiPaint.mix(ClickGuiPalette.SURFACE_BOTTOM, ClickGuiPalette.SURFACE_ALT_BOTTOM, Math.max(hover * 0.55F, expand * 0.5F));
            int border = ClickGuiPaint.mix(ClickGuiPalette.BORDER_SOFT, ClickGuiPalette.withAlpha(ClickGuiPalette.ACCENT, 180), Math.max(enabled * 0.45F, expand * 0.85F));
            ClickGuiPaint.drawPanel(drawContext, rowRect, topFill, bottomFill, border, ClickGuiPalette.INNER_HIGHLIGHT, ClickGuiPalette.PANEL_RADIUS);

            if (enabled > 0.02F) {
                ClickGuiLayout.Rect accentRect = new ClickGuiLayout.Rect(rowRect.x() + 1, rowRect.y() + 5, 3, rowRect.height() - 10);
                ClickGuiPaint.drawGlow(drawContext, accentRect, 4, ClickGuiPalette.withAlpha(ClickGuiPalette.ACCENT, Math.round(64 * enabled)));
                drawContext.fill(accentRect.x(), accentRect.y(), accentRect.right(), accentRect.bottom(), ClickGuiPalette.ACCENT);
            }

            ClickGuiLayout.Rect toggleRect = toggleRect(rowRect);
            int textRight = toggleRect.x() - 12;
            renderModuleLabel(drawContext, module, rowRect, textRight, enabled);
            renderToggle(drawContext, toggleRect, enabled);

            rowY += ClickGuiPalette.MODULE_ROW_HEIGHT + 8;
            if (expand > 0.01F) {
                int visibleHeight = Math.max(1, Math.round(measureExpandedHeight(module, contentWidth) * expand));
                renderExpandedModule(drawContext, module, rowX, rowY, contentWidth, visibleHeight);
                rowY += visibleHeight + 8;
            }
        }
        drawContext.disableScissor();

        if (maxScroll > 0.0D) {
            int trackX = layout.list().right() - 4;
            int trackHeight = layout.list().height() - 10;
            int thumbHeight = Math.max(28, (int) Math.round(trackHeight * (trackHeight / (double) (trackHeight + maxScroll))));
            int thumbY = layout.list().y() + 5 + (int) Math.round((trackHeight - thumbHeight) * (renderScroll / maxScroll));
            drawContext.fill(trackX, layout.list().y() + 5, trackX + 2, layout.list().bottom() - 5, ClickGuiPalette.BORDER_SOFT);
            drawContext.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, ClickGuiPalette.ACCENT);
        }
    }

    private void renderModuleLabel(DrawContext drawContext, Module module, ClickGuiLayout.Rect rowRect, int maxRight, float enabledProgress) {
        int nameX = rowRect.x() + 12;
        int nameY = rowRect.y() + 8;
        int nameColor = ClickGuiPaint.mix(ClickGuiPalette.TEXT_PRIMARY, ClickGuiPalette.ACCENT, enabledProgress);
        drawStyledText(drawContext, module.name(), nameX, nameY, nameColor, ClickFont.BODY);

        int labelEnd = nameX + textWidth(module.name(), ClickFont.BODY);
        String suffix = module.displaySuffix();
        int metaRight = maxRight;
        if (suffix != null && !suffix.isBlank()) {
            int suffixWidth = textWidth(suffix, ClickFont.SMALL);
            int suffixX = maxRight - suffixWidth;
            if (suffixX > labelEnd + MODULE_SUFFIX_GAP) {
                drawStyledText(drawContext, suffix, suffixX, rowRect.y() + 9, enabledProgress > 0.5F ? ClickGuiPalette.ACCENT : ClickGuiPalette.TEXT_DISABLED, ClickFont.SMALL);
                metaRight = suffixX - 10;
            }
        }

        if (showAllCategories || isSearchActive()) {
            String categoryLabel = module.category().displayName();
            int categoryWidth = textWidth(categoryLabel, ClickFont.SMALL);
            int categoryX = metaRight - categoryWidth;
            if (categoryX > labelEnd + 12) {
                drawStyledText(drawContext, categoryLabel, categoryX, rowRect.y() + 24, ClickGuiPalette.TEXT_DISABLED, ClickFont.SMALL);
                metaRight = categoryX - 8;
            }
        }

        String preview = trimToWidth(module.description(), Math.max(36, metaRight - nameX), ClickFont.SMALL);
        drawStyledText(drawContext, preview, nameX, rowRect.y() + 25, ClickGuiPalette.TEXT_MUTED, ClickFont.SMALL);
    }

    private int measureModuleListHeight(List<Module> modules, int width) {
        int total = 2;
        for (Module module : modules) {
            total += ClickGuiPalette.MODULE_ROW_HEIGHT + 8;
            float expand = moduleExpandAnimations.getOrDefault(module.id(), module.id().equals(expandedModuleId) ? 1.0F : 0.0F);
            if (expand > 0.0F) {
                total += Math.round(measureExpandedHeight(module, width) * expand) + 8;
            }
        }
        return total;
    }

    private int measureExpandedHeight(Module module, int width) {
        int total = 10;
        total += wrapLines(module.description(), Math.max(40, width - 20), ClickFont.SMALL).size() * (textLineHeight(ClickFont.SMALL) + 2);
        String suffix = module.displaySuffix();
        total += suffix == null || suffix.isBlank() ? 6 : textLineHeight(ClickFont.SMALL) + 8;
        if (!visibleSettingRows(module).isEmpty()) {
            total += 8;
        }
        for (SettingRow row : visibleSettingRows(module)) {
            total += measureSettingHeight(module, row);
            total += 6;
        }
        return total + 6;
    }

    private void renderExpandedModule(DrawContext drawContext, Module module, int x, int y, int width, int visibleHeight) {
        int fullHeight = measureExpandedHeight(module, width);
        ClickGuiLayout.Rect fullRect = new ClickGuiLayout.Rect(x, y, width, fullHeight);
        ClickGuiLayout.Rect visibleRect = new ClickGuiLayout.Rect(x, y, width, visibleHeight);
        drawContext.enableScissor(visibleRect.x(), visibleRect.y(), visibleRect.right(), visibleRect.bottom());
        ClickGuiPaint.drawShadow(drawContext, fullRect, 6, ClickGuiPalette.withAlpha(ClickGuiPalette.SHADOW, 118));
        ClickGuiPaint.drawPanel(drawContext, fullRect, ClickGuiPalette.SURFACE_TOP, ClickGuiPalette.SURFACE_BOTTOM, ClickGuiPalette.BORDER_SOFT, ClickGuiPalette.INNER_HIGHLIGHT, ClickGuiPalette.PANEL_RADIUS);
        drawContext.fill(fullRect.x(), fullRect.y(), fullRect.right(), fullRect.y() + 1, ClickGuiPalette.withAlpha(ClickGuiPalette.ACCENT, 180));

        int cursorY = fullRect.y() + 10;
        int contentX = fullRect.x() + 10;
        int contentWidth = fullRect.width() - 20;
        for (String line : wrapLines(module.description(), contentWidth, ClickFont.SMALL)) {
            drawStyledText(drawContext, line, contentX, cursorY, ClickGuiPalette.TEXT_MUTED, ClickFont.SMALL);
            cursorY += textLineHeight(ClickFont.SMALL) + 2;
        }

        String suffix = module.displaySuffix();
        if (suffix != null && !suffix.isBlank()) {
            cursorY += 2;
            drawStyledText(drawContext, suffix, contentX, cursorY, ClickGuiPalette.ACCENT, ClickFont.SMALL);
            cursorY += textLineHeight(ClickFont.SMALL) + 6;
        } else {
            cursorY += 6;
        }

        if (!visibleSettingRows(module).isEmpty()) {
            drawContext.fill(contentX, cursorY, contentX + contentWidth, cursorY + 1, ClickGuiPalette.BORDER_SOFT);
            cursorY += 8;
        }

        for (SettingRow row : visibleSettingRows(module)) {
            if (row instanceof NumberRangeRow rangeRow) {
                renderNumberRangeSetting(drawContext, rangeRow, contentX, cursorY, contentWidth);
            } else if (row instanceof SingleSettingRow singleRow) {
                Setting<?> setting = singleRow.setting();
                if (setting instanceof BoolSetting boolSetting) {
                    renderBoolSetting(drawContext, boolSetting, contentX, cursorY, contentWidth);
                } else if (setting instanceof NumberSetting numberSetting) {
                    renderNumberSetting(drawContext, numberSetting, contentX, cursorY, contentWidth);
                } else if (setting instanceof EnumSetting<?> enumSetting) {
                    renderEnumSetting(drawContext, enumSetting, contentX, cursorY, contentWidth);
                } else if (setting instanceof ColorSetting colorSetting) {
                    renderColorSetting(drawContext, module, colorSetting, contentX, cursorY, contentWidth);
                } else if (setting instanceof KeyBindSetting keyBindSetting) {
                    renderKeybindSetting(drawContext, module, keyBindSetting, contentX, cursorY, contentWidth);
                }
            }
            cursorY += measureSettingHeight(module, row) + 6;
        }
        drawContext.disableScissor();
    }

    private int measureSettingHeight(Module module, Setting<?> setting) {
        return switch (setting) {
            case NumberSetting ignored -> ClickGuiPalette.NUMBER_ROW_HEIGHT;
            case ColorSetting colorSetting -> ClickGuiPalette.SETTING_ROW_HEIGHT
                    + (settingPath(module, colorSetting).equals(openColorSettingPath) ? 6 + (ClickGuiPalette.COLOR_CHANNEL_HEIGHT * 4) + 12 : 0);
            default -> ClickGuiPalette.SETTING_ROW_HEIGHT;
        };
    }

    private int measureSettingHeight(Module module, SettingRow row) {
        if (row instanceof NumberRangeRow) {
            return ClickGuiPalette.NUMBER_ROW_HEIGHT;
        }
        return measureSettingHeight(module, ((SingleSettingRow) row).setting());
    }

    private void renderBoolSetting(DrawContext drawContext, BoolSetting setting, int x, int y, int width) {
        ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(x, y, width, ClickGuiPalette.SETTING_ROW_HEIGHT);
        ClickGuiPaint.drawPanel(drawContext, rowRect, ClickGuiPalette.SURFACE_ALT_TOP, ClickGuiPalette.SURFACE_ALT_BOTTOM, ClickGuiPalette.BORDER_SOFT, ClickGuiPalette.INNER_HIGHLIGHT, ClickGuiPalette.CONTROL_RADIUS);
        drawStyledText(drawContext, setting.label(), rowRect.x() + 10, centeredTextY(rowRect, ClickFont.BODY), ClickGuiPalette.TEXT_PRIMARY, ClickFont.BODY);
        renderToggle(drawContext, toggleRect(rowRect), setting.value() ? 1.0F : 0.0F);
    }

    private void renderNumberSetting(DrawContext drawContext, NumberSetting setting, int x, int y, int width) {
        ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(x, y, width, ClickGuiPalette.NUMBER_ROW_HEIGHT);
        ClickGuiPaint.drawPanel(drawContext, rowRect, ClickGuiPalette.SURFACE_ALT_TOP, ClickGuiPalette.SURFACE_ALT_BOTTOM, ClickGuiPalette.BORDER_SOFT, ClickGuiPalette.INNER_HIGHLIGHT, ClickGuiPalette.CONTROL_RADIUS);
        String value = setting.displayValue();
        drawStyledText(drawContext, setting.label(), rowRect.x() + 10, rowRect.y() + 6, ClickGuiPalette.TEXT_PRIMARY, ClickFont.BODY);
        drawStyledText(drawContext, value, rowRect.right() - textWidth(value, ClickFont.SMALL) - 10, rowRect.y() + 7, ClickGuiPalette.TEXT_SECONDARY, ClickFont.SMALL);
        renderSlider(drawContext, sliderRect(rowRect), normalized(setting), ClickGuiPalette.TRACK_FILL);
    }

    private void renderNumberRangeSetting(DrawContext drawContext, NumberRangeRow row, int x, int y, int width) {
        ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(x, y, width, ClickGuiPalette.NUMBER_ROW_HEIGHT);
        ClickGuiPaint.drawPanel(drawContext, rowRect, ClickGuiPalette.SURFACE_ALT_TOP, ClickGuiPalette.SURFACE_ALT_BOTTOM, ClickGuiPalette.BORDER_SOFT, ClickGuiPalette.INNER_HIGHLIGHT, ClickGuiPalette.CONTROL_RADIUS);
        String value = row.minSetting().displayValue() + " - " + row.maxSetting().displayValue();
        drawStyledText(drawContext, row.label(), rowRect.x() + 10, rowRect.y() + 6, ClickGuiPalette.TEXT_PRIMARY, ClickFont.BODY);
        drawStyledText(drawContext, value, rowRect.right() - textWidth(value, ClickFont.SMALL) - 10, rowRect.y() + 7, ClickGuiPalette.TEXT_SECONDARY, ClickFont.SMALL);
        renderRangeSlider(drawContext, sliderRect(rowRect), normalized(row.minSetting()), normalized(row.maxSetting()), ClickGuiPalette.TRACK_FILL);
    }

    private void renderEnumSetting(DrawContext drawContext, EnumSetting<?> setting, int x, int y, int width) {
        ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(x, y, width, ClickGuiPalette.SETTING_ROW_HEIGHT);
        ClickGuiPaint.drawPanel(drawContext, rowRect, ClickGuiPalette.SURFACE_ALT_TOP, ClickGuiPalette.SURFACE_ALT_BOTTOM, ClickGuiPalette.BORDER_SOFT, ClickGuiPalette.INNER_HIGHLIGHT, ClickGuiPalette.CONTROL_RADIUS);
        drawStyledText(drawContext, setting.label(), rowRect.x() + 10, centeredTextY(rowRect, ClickFont.BODY), ClickGuiPalette.TEXT_PRIMARY, ClickFont.BODY);
        ClickGuiLayout.Rect buttonRect = actionRect(rowRect);
        ClickGuiPaint.drawPanel(drawContext, buttonRect, ClickGuiPalette.SURFACE_TOP, ClickGuiPalette.SURFACE_BOTTOM, ClickGuiPalette.BORDER_SOFT, ClickGuiPalette.INNER_HIGHLIGHT, ClickGuiPalette.CONTROL_RADIUS);
        String value = setting.displayValue();
        drawStyledText(drawContext, "<", buttonRect.x() + 8, centeredTextY(buttonRect, ClickFont.SMALL), ClickGuiPalette.TEXT_MUTED, ClickFont.SMALL);
        drawStyledText(drawContext, value, buttonRect.x() + (buttonRect.width() - textWidth(value, ClickFont.SMALL)) / 2, centeredTextY(buttonRect, ClickFont.SMALL), ClickGuiPalette.TEXT_PRIMARY, ClickFont.SMALL);
        drawStyledText(drawContext, ">", buttonRect.right() - 12, centeredTextY(buttonRect, ClickFont.SMALL), ClickGuiPalette.TEXT_MUTED, ClickFont.SMALL);
    }

    private void renderColorSetting(DrawContext drawContext, Module module, ColorSetting setting, int x, int y, int width) {
        ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(x, y, width, ClickGuiPalette.SETTING_ROW_HEIGHT);
        ClickGuiPaint.drawPanel(drawContext, rowRect, ClickGuiPalette.SURFACE_ALT_TOP, ClickGuiPalette.SURFACE_ALT_BOTTOM, ClickGuiPalette.BORDER_SOFT, ClickGuiPalette.INNER_HIGHLIGHT, ClickGuiPalette.CONTROL_RADIUS);
        drawStyledText(drawContext, setting.label(), rowRect.x() + 10, centeredTextY(rowRect, ClickFont.BODY), ClickGuiPalette.TEXT_PRIMARY, ClickFont.BODY);
        ClickGuiLayout.Rect buttonRect = actionRect(rowRect);
        ClickGuiPaint.drawPanel(drawContext, buttonRect, ClickGuiPalette.SURFACE_TOP, ClickGuiPalette.SURFACE_BOTTOM, ClickGuiPalette.BORDER_SOFT, ClickGuiPalette.INNER_HIGHLIGHT, ClickGuiPalette.CONTROL_RADIUS);
        drawContext.fill(buttonRect.x() + 6, buttonRect.y() + 4, buttonRect.x() + 20, buttonRect.bottom() - 4, setting.value());
        drawStyledText(drawContext, setting.displayValue(), buttonRect.x() + 26, centeredTextY(buttonRect, ClickFont.SMALL), ClickGuiPalette.TEXT_PRIMARY, ClickFont.SMALL);

        if (settingPath(module, setting).equals(openColorSettingPath)) {
            int channelY = rowRect.bottom() + 6;
            renderColorChannel(drawContext, setting, ColorChannel.ALPHA, x, channelY, width);
            renderColorChannel(drawContext, setting, ColorChannel.RED, x, channelY + 30, width);
            renderColorChannel(drawContext, setting, ColorChannel.GREEN, x, channelY + 60, width);
            renderColorChannel(drawContext, setting, ColorChannel.BLUE, x, channelY + 90, width);
        }
    }

    private void renderColorChannel(DrawContext drawContext, ColorSetting setting, ColorChannel channel, int x, int y, int width) {
        ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(x, y, width, ClickGuiPalette.COLOR_CHANNEL_HEIGHT);
        ClickGuiPaint.drawPanel(drawContext, rowRect, ClickGuiPalette.SURFACE_TOP, ClickGuiPalette.SURFACE_BOTTOM, ClickGuiPalette.BORDER_SOFT, ClickGuiPalette.INNER_HIGHLIGHT, ClickGuiPalette.CONTROL_RADIUS);
        String label = channel.label + " " + channel.value(setting.value());
        drawStyledText(drawContext, label, rowRect.x() + 10, rowRect.y() + 5, ClickGuiPalette.TEXT_SECONDARY, ClickFont.SMALL);
        renderSlider(drawContext, colorSliderRect(rowRect), channel.value(setting.value()) / 255.0D, channel.color);
    }

    private void renderKeybindSetting(DrawContext drawContext, Module module, KeyBindSetting setting, int x, int y, int width) {
        ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(x, y, width, ClickGuiPalette.SETTING_ROW_HEIGHT);
        ClickGuiPaint.drawPanel(drawContext, rowRect, ClickGuiPalette.SURFACE_ALT_TOP, ClickGuiPalette.SURFACE_ALT_BOTTOM, ClickGuiPalette.BORDER_SOFT, ClickGuiPalette.INNER_HIGHLIGHT, ClickGuiPalette.CONTROL_RADIUS);
        drawStyledText(drawContext, setting.label(), rowRect.x() + 10, centeredTextY(rowRect, ClickFont.BODY), ClickGuiPalette.TEXT_PRIMARY, ClickFont.BODY);
        ClickGuiLayout.Rect buttonRect = actionRect(rowRect);
        boolean pending = settingPath(module, setting).equals(pendingKeybindPath);
        ClickGuiPaint.drawPanel(
                drawContext,
                buttonRect,
                ClickGuiPalette.SURFACE_TOP,
                ClickGuiPalette.SURFACE_BOTTOM,
                pending ? ClickGuiPalette.ACCENT : ClickGuiPalette.BORDER_SOFT,
                ClickGuiPalette.INNER_HIGHLIGHT,
                ClickGuiPalette.CONTROL_RADIUS
        );
        String value = pending ? "Press a key" : setting.displayValue();
        drawStyledText(
                drawContext,
                value,
                buttonRect.x() + (buttonRect.width() - textWidth(value, ClickFont.SMALL)) / 2,
                centeredTextY(buttonRect, ClickFont.SMALL),
                pending ? ClickGuiPalette.TEXT_PRIMARY : ClickGuiPalette.TEXT_SECONDARY,
                ClickFont.SMALL
        );
    }

    private boolean handleSearchKeyPress(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_A && hasControlDown()) {
            if (!searchQuery.isEmpty()) {
                searchSelectAll = true;
                searchCaret = searchQuery.length();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_V && hasControlDown()) {
            String clipboard = MinecraftClient.getInstance().keyboard.getClipboard();
            if (clipboard != null && !clipboard.isBlank()) {
                replaceSelectionIfNeeded();
                String filtered = clipboard.replaceAll("[\\r\\n\\t]", " ");
                int allowedLength = SEARCH_MAX_LENGTH - searchQuery.length();
                if (allowedLength > 0) {
                    String insertion = filtered.length() > allowedLength ? filtered.substring(0, allowedLength) : filtered;
                    searchQuery = searchQuery.substring(0, searchCaret) + insertion + searchQuery.substring(searchCaret);
                    searchCaret += insertion.length();
                    listScroll = 0.0D;
                    clearInvalidExpandedModule();
                }
            }
            return true;
        }
        return switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (searchSelectAll) {
                    clearSearch();
                } else if (searchCaret > 0) {
                    searchQuery = searchQuery.substring(0, searchCaret - 1) + searchQuery.substring(searchCaret);
                    searchCaret--;
                    listScroll = 0.0D;
                    clearInvalidExpandedModule();
                }
                yield true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (searchSelectAll) {
                    clearSearch();
                } else if (searchCaret < searchQuery.length()) {
                    searchQuery = searchQuery.substring(0, searchCaret) + searchQuery.substring(searchCaret + 1);
                    listScroll = 0.0D;
                    clearInvalidExpandedModule();
                }
                yield true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                searchSelectAll = false;
                searchCaret = Math.max(0, searchCaret - 1);
                yield true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                searchSelectAll = false;
                searchCaret = Math.min(searchQuery.length(), searchCaret + 1);
                yield true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                searchSelectAll = false;
                searchCaret = 0;
                yield true;
            }
            case GLFW.GLFW_KEY_END -> {
                searchSelectAll = false;
                searchCaret = searchQuery.length();
                yield true;
            }
            default -> false;
        };
    }

    private boolean handleCategoryClick(double mouseX, double mouseY) {
        for (FilterChip chip : filterChips) {
            if (!chip.rect().contains(mouseX, mouseY)) {
                continue;
            }
            applyFilterId(chip.filterId());
            listScroll = 0.0D;
            expandedModuleId = null;
            openColorSettingPath = null;
            pendingKeybindPath = null;
            activeDrag = null;
            return true;
        }
        return false;
    }

    private boolean handleModuleClick(double mouseX, double mouseY) {
        List<Module> modules = filteredModules();
        int contentWidth = layout.list().width() - 12 - scrollbarWidth(modules);
        int rowX = layout.list().x() + 2;
        int rowY = layout.list().y() + 2 - (int) Math.round(renderScroll);

        for (Module module : modules) {
            ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(rowX, rowY, contentWidth, ClickGuiPalette.MODULE_ROW_HEIGHT);
            ClickGuiLayout.Rect toggleRect = toggleRect(rowRect);
            if (toggleRect.contains(mouseX, mouseY)) {
                runtime.moduleManager().toggle(module);
                return true;
            }
            if (rowRect.contains(mouseX, mouseY)) {
                expandedModuleId = module.id().equals(expandedModuleId) ? null : module.id();
                openColorSettingPath = null;
                pendingKeybindPath = null;
                activeDrag = null;
                return true;
            }

            rowY += ClickGuiPalette.MODULE_ROW_HEIGHT + 8;
            float expand = moduleExpandAnimations.getOrDefault(module.id(), 0.0F);
            if (module.id().equals(expandedModuleId) && expand >= 0.98F) {
                if (handleExpandedClick(module, mouseX, mouseY, rowX, rowY, contentWidth)) {
                    return true;
                }
            }
            if (expand > 0.0F) {
                rowY += Math.round(measureExpandedHeight(module, contentWidth) * expand) + 8;
            }
        }
        return false;
    }

    private boolean handleExpandedClick(Module module, double mouseX, double mouseY, int x, int y, int width) {
        int cursorY = y + 10;
        cursorY += wrapLines(module.description(), Math.max(40, width - 20), ClickFont.SMALL).size() * (textLineHeight(ClickFont.SMALL) + 2);
        String suffix = module.displaySuffix();
        cursorY += suffix == null || suffix.isBlank() ? 6 : textLineHeight(ClickFont.SMALL) + 8;
        if (!visibleSettingRows(module).isEmpty()) {
            cursorY += 8;
        }

        for (SettingRow row : visibleSettingRows(module)) {
            ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(x + 10, cursorY, width - 20, measureSettingHeight(module, row));
            if (row instanceof NumberRangeRow rangeRow) {
                ClickGuiLayout.Rect sliderRect = sliderRect(rowRect);
                if (sliderRect.contains(mouseX, mouseY)) {
                    activeDrag = new NumberRangeDrag(sliderRect, rangeRow.minSetting(), rangeRow.maxSetting(), closestHandle(sliderRect, rangeRow, mouseX));
                    activeDrag.update(mouseX);
                    return true;
                }
            } else if (row instanceof SingleSettingRow singleRow) {
                Setting<?> setting = singleRow.setting();
                if (setting instanceof BoolSetting boolSetting) {
                    if (toggleRect(rowRect).contains(mouseX, mouseY)) {
                        boolSetting.toggle();
                        return true;
                    }
                } else if (setting instanceof NumberSetting numberSetting) {
                    ClickGuiLayout.Rect sliderRect = sliderRect(rowRect);
                    if (sliderRect.contains(mouseX, mouseY)) {
                        activeDrag = new NumberDrag(sliderRect, numberSetting);
                        activeDrag.update(mouseX);
                        return true;
                    }
                } else if (setting instanceof EnumSetting<?> enumSetting) {
                    ClickGuiLayout.Rect actionRect = actionRect(rowRect);
                    if (actionRect.contains(mouseX, mouseY)) {
                        enumSetting.cycle(mouseX >= actionRect.centerX());
                        return true;
                    }
                } else if (setting instanceof ColorSetting colorSetting) {
                    ClickGuiLayout.Rect actionRect = actionRect(new ClickGuiLayout.Rect(rowRect.x(), rowRect.y(), rowRect.width(), ClickGuiPalette.SETTING_ROW_HEIGHT));
                    if (actionRect.contains(mouseX, mouseY)) {
                        String path = settingPath(module, colorSetting);
                        openColorSettingPath = path.equals(openColorSettingPath) ? null : path;
                        activeDrag = null;
                        return true;
                    }
                    if (settingPath(module, colorSetting).equals(openColorSettingPath)) {
                        for (int index = 0; index < 4; index++) {
                            ColorChannel channel = ColorChannel.values()[index];
                            ClickGuiLayout.Rect sliderRect = colorSliderRect(new ClickGuiLayout.Rect(rowRect.x(), rowRect.y() + 30 + (index * 30), rowRect.width(), ClickGuiPalette.COLOR_CHANNEL_HEIGHT));
                            if (sliderRect.contains(mouseX, mouseY)) {
                                activeDrag = new ColorDrag(sliderRect, colorSetting, channel);
                                activeDrag.update(mouseX);
                                return true;
                            }
                        }
                    }
                } else if (setting instanceof KeyBindSetting) {
                    if (actionRect(rowRect).contains(mouseX, mouseY)) {
                        String path = settingPath(module, setting);
                        pendingKeybindPath = path.equals(pendingKeybindPath) ? null : path;
                        return true;
                    }
                }
            }
            cursorY += measureSettingHeight(module, row) + 6;
        }
        return false;
    }

    private ClickGuiLayout.Rect toggleRect(ClickGuiLayout.Rect rowRect) {
        return new ClickGuiLayout.Rect(
                rowRect.right() - ClickGuiPalette.TOGGLE_WIDTH - 10,
                rowRect.y() + Math.max(2, (rowRect.height() - ClickGuiPalette.TOGGLE_HEIGHT) / 2),
                ClickGuiPalette.TOGGLE_WIDTH,
                ClickGuiPalette.TOGGLE_HEIGHT
        );
    }

    private ClickGuiLayout.Rect actionRect(ClickGuiLayout.Rect rowRect) {
        int buttonWidth = Math.min(112, Math.max(84, rowRect.width() / 2));
        int buttonHeight = Math.max(18, ClickGuiPalette.SETTING_ROW_HEIGHT - 8);
        return new ClickGuiLayout.Rect(
                rowRect.right() - buttonWidth - 8,
                rowRect.y() + Math.max(2, (rowRect.height() - buttonHeight) / 2),
                buttonWidth,
                buttonHeight
        );
    }

    private ClickGuiLayout.Rect sliderRect(ClickGuiLayout.Rect rowRect) {
        return new ClickGuiLayout.Rect(rowRect.x() + 10, rowRect.bottom() - 10, rowRect.width() - 20, 6);
    }

    private ClickGuiLayout.Rect colorSliderRect(ClickGuiLayout.Rect rowRect) {
        return new ClickGuiLayout.Rect(rowRect.x() + 10, rowRect.bottom() - 10, rowRect.width() - 20, 6);
    }

    private void renderToggle(DrawContext drawContext, ClickGuiLayout.Rect rect, float enabledProgress) {
        float clamped = Math.max(0.0F, Math.min(1.0F, enabledProgress));
        int trackTop = ClickGuiPaint.mix(ClickGuiPalette.SWITCH_OFF, ClickGuiPaint.mix(ClickGuiPalette.ACCENT, 0xFFFFFFFF, 0.10F), clamped);
        int trackBottom = ClickGuiPaint.mix(ClickGuiPalette.SWITCH_OFF, ClickGuiPalette.ACCENT, clamped);
        int border = ClickGuiPaint.mix(ClickGuiPalette.BORDER_SOFT, ClickGuiPalette.withAlpha(ClickGuiPalette.ACCENT, 200), clamped);
        if (clamped > 0.08F) {
            ClickGuiPaint.drawGlow(drawContext, rect, 5, ClickGuiPalette.withAlpha(ClickGuiPalette.ACCENT, Math.round(54 * clamped)));
        }
        ClickGuiPaint.drawPanel(drawContext, rect, trackTop, trackBottom, border, ClickGuiPalette.withAlpha(ClickGuiPalette.TEXT_PRIMARY, 18), ClickGuiPalette.CONTROL_RADIUS);

        int knobWidth = 14;
        int knobX = rect.x() + 2 + Math.round((rect.width() - knobWidth - 4) * clamped);
        drawContext.fill(knobX, rect.y() + 2, knobX + knobWidth, rect.bottom() - 2, ClickGuiPalette.SWITCH_KNOB);
        drawContext.fill(knobX, rect.y() + 2, knobX + knobWidth, rect.y() + 3, ClickGuiPalette.withAlpha(0xFFFFFFFF, 58));
    }

    private void renderSlider(DrawContext drawContext, ClickGuiLayout.Rect rect, double normalized, int fillColor) {
        ClickGuiPaint.drawPanel(drawContext, rect, ClickGuiPalette.TRACK, ClickGuiPalette.TRACK, ClickGuiPalette.BORDER_SOFT, 0, ClickGuiPalette.CONTROL_RADIUS);
        int fillWidth = (int) Math.round(rect.width() * MathHelper.clamp((float) normalized, 0.0F, 1.0F));
        if (fillWidth > 0) {
            drawContext.fill(rect.x(), rect.y(), rect.x() + fillWidth, rect.bottom(), fillColor);
        }
        renderSliderHandle(drawContext, rect, rect.x() + fillWidth, fillColor);
    }

    private void renderRangeSlider(DrawContext drawContext, ClickGuiLayout.Rect rect, double lowerNormalized, double upperNormalized, int fillColor) {
        ClickGuiPaint.drawPanel(drawContext, rect, ClickGuiPalette.TRACK, ClickGuiPalette.TRACK, ClickGuiPalette.BORDER_SOFT, 0, ClickGuiPalette.CONTROL_RADIUS);
        int lowerX = sliderPosition(rect, lowerNormalized);
        int upperX = sliderPosition(rect, upperNormalized);
        if (lowerX > upperX) {
            int swap = lowerX;
            lowerX = upperX;
            upperX = swap;
        }
        drawContext.fill(lowerX, rect.y(), Math.max(lowerX + 1, upperX), rect.bottom(), fillColor);
        renderSliderHandle(drawContext, rect, lowerX, fillColor);
        renderSliderHandle(drawContext, rect, upperX, fillColor);
    }

    private void renderSliderHandle(DrawContext drawContext, ClickGuiLayout.Rect rect, int centerX, int color) {
        int knobX = MathHelper.clamp(centerX - 4, rect.x(), rect.right() - 8);
        ClickGuiLayout.Rect knobRect = new ClickGuiLayout.Rect(knobX, rect.y() - 2, 8, rect.height() + 4);
        ClickGuiPaint.drawGlow(drawContext, knobRect, 4, ClickGuiPalette.withAlpha(color, 48));
        drawContext.fill(knobRect.x(), knobRect.y(), knobRect.right(), knobRect.bottom(), ClickGuiPalette.SWITCH_KNOB);
    }

    private int sliderPosition(ClickGuiLayout.Rect rect, double normalized) {
        return MathHelper.clamp(
                rect.x() + (int) Math.round(rect.width() * MathHelper.clamp((float) normalized, 0.0F, 1.0F)),
                rect.x(),
                rect.right()
        );
    }

    private void focusSearchAt(double mouseX) {
        searchFocused = true;
        searchSelectAll = false;
        searchCaret = caretIndexAt(layout.search(), mouseX);
    }

    private int caretIndexAt(ClickGuiLayout.Rect searchRect, double mouseX) {
        if (searchQuery.isEmpty()) {
            return 0;
        }
        int relativeX = Math.max(0, (int) Math.round(mouseX - (searchRect.x() + 14)));
        for (int index = 0; index < searchQuery.length(); index++) {
            if (relativeX < textWidth(searchQuery.substring(0, index + 1), ClickFont.BODY)) {
                return index;
            }
        }
        return searchQuery.length();
    }

    private void clearSearch() {
        searchQuery = "";
        searchCaret = 0;
        searchSelectAll = false;
        listScroll = 0.0D;
        clearInvalidExpandedModule();
    }

    private void replaceSelectionIfNeeded() {
        if (searchSelectAll) {
            searchQuery = "";
            searchCaret = 0;
            searchSelectAll = false;
        }
    }

    private SearchView computeSearchView(int availableWidth) {
        if (searchQuery.isEmpty()) {
            return new SearchView("", 0);
        }

        int start = 0;
        while (start < searchCaret && textWidth(searchQuery.substring(start, searchCaret), ClickFont.BODY) > availableWidth) {
            start++;
        }

        int end = start;
        while (end < searchQuery.length()) {
            String candidate = searchQuery.substring(start, end + 1);
            if (textWidth(candidate, ClickFont.BODY) > availableWidth) {
                break;
            }
            end++;
        }

        String visible = searchQuery.substring(start, end);
        int caretOffset = textWidth(searchQuery.substring(start, Math.max(start, Math.min(searchCaret, end))), ClickFont.BODY);
        return new SearchView(visible, caretOffset);
    }

    private boolean caretVisible() {
        return (System.currentTimeMillis() / 500L) % 2L == 0L;
    }

    private void updateAnimations(int mouseX, int mouseY, float frameDelta, List<Module> modules) {
        for (FilterChip chip : filterChips) {
            animateMap(filterHoverAnimations, chip.filterId(), chip.rect().contains(mouseX, mouseY) ? 1.0F : 0.0F, frameDelta, 14.0F);
            animateMap(filterSelectAnimations, chip.filterId(), chip.filterId().equals(currentFilterId()) ? 1.0F : 0.0F, frameDelta, 10.0F);
        }

        for (Module module : runtime.moduleManager().all()) {
            animateMap(moduleEnabledAnimations, module.id(), module.enabled() ? 1.0F : 0.0F, frameDelta, 10.0F);
            animateMap(moduleExpandAnimations, module.id(), module.id().equals(expandedModuleId) ? 1.0F : 0.0F, frameDelta, 10.0F);
        }

        int contentWidth = layout.list().width() - 12 - scrollbarWidth(modules);
        int rowX = layout.list().x() + 2;
        int rowY = layout.list().y() + 2 - (int) Math.round(renderScroll);
        for (Module module : modules) {
            ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(rowX, rowY, contentWidth, ClickGuiPalette.MODULE_ROW_HEIGHT);
            animateMap(moduleHoverAnimations, module.id(), rowRect.contains(mouseX, mouseY) ? 1.0F : 0.0F, frameDelta, 14.0F);
            rowY += ClickGuiPalette.MODULE_ROW_HEIGHT + 8;
            float expand = moduleExpandAnimations.getOrDefault(module.id(), 0.0F);
            if (expand > 0.0F) {
                rowY += Math.round(measureExpandedHeight(module, contentWidth) * expand) + 8;
            }
        }
    }

    private void buildFilterChips(ClickGuiLayout layout) {
        filterChips.clear();
        List<FilterSpec> specs = new ArrayList<>();
        specs.add(new FilterSpec(FILTER_ALL, "All"));
        for (ModuleCategory category : ModuleCategory.values()) {
            specs.add(new FilterSpec(category.name().toLowerCase(Locale.ROOT), category.displayName()));
        }

        int gap = 8;
        int totalWidth = -gap;
        for (FilterSpec spec : specs) {
            totalWidth += textWidth(spec.label(), ClickFont.BODY) + 26 + gap;
        }

        int x = layout.tabs().x() + Math.max(0, (layout.tabs().width() - totalWidth) / 2);
        for (FilterSpec spec : specs) {
            int width = textWidth(spec.label(), ClickFont.BODY) + 26;
            filterChips.add(new FilterChip(spec.filterId(), spec.label(), new ClickGuiLayout.Rect(x, layout.tabs().y(), width, ClickGuiPalette.TAB_HEIGHT)));
            x += width + gap;
        }
    }

    private float frameDeltaSeconds() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return 1.0F / 60.0F;
        }
        float delta = (now - lastFrameNanos) / 1_000_000_000.0F;
        lastFrameNanos = now;
        return Math.max(1.0F / 240.0F, Math.min(0.05F, delta));
    }

    private void finishClose() {
        runtime.configService().putUiValue(UI_SECTION, FILTER_KEY, new JsonPrimitive(currentFilterId()));
        runtime.configService().save(runtime.moduleManager());
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen == this) {
            client.setScreen(null);
        }
    }

    private void clearInvalidExpandedModule() {
        if (expandedModuleId != null && filteredModules().stream().noneMatch(module -> module.id().equals(expandedModuleId))) {
            expandedModuleId = null;
            openColorSettingPath = null;
            pendingKeybindPath = null;
            activeDrag = null;
        }
    }

    private int scrollbarWidth(List<Module> modules) {
        return modules.isEmpty() ? 0 : 6;
    }

    private int scaleAlpha(int color, float amount) {
        int alpha = (color >>> 24) & 0xFF;
        return ClickGuiPalette.withAlpha(color, Math.round(alpha * Math.max(0.0F, Math.min(1.0F, amount))));
    }

    private float easeOut(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        float inverse = 1.0F - clamped;
        return 1.0F - (inverse * inverse * inverse);
    }

    private float animateValue(float current, float target, float deltaSeconds, float speed) {
        return current + ((target - current) * Math.min(1.0F, deltaSeconds * speed));
    }

    private double animateDouble(double current, double target, float deltaSeconds, float speed) {
        return current + ((target - current) * Math.min(1.0D, deltaSeconds * speed));
    }

    private void animateMap(Map<String, Float> map, String key, float target, float deltaSeconds, float speed) {
        float current = map.getOrDefault(key, 0.0F);
        float next = animateValue(current, target, deltaSeconds, speed);
        if (Math.abs(next - target) < 0.01F) {
            next = target;
        }
        if (next <= 0.001F && target <= 0.001F) {
            map.remove(key);
            return;
        }
        map.put(key, next);
    }

    private void restoreFilter() {
        runtime.configService()
                .getUiValue(UI_SECTION, FILTER_KEY)
                .filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsString)
                .ifPresentOrElse(this::applyFilterId, () -> {
                    showAllCategories = true;
                    selectedCategory = ModuleCategory.COMBAT;
                });
    }

    private void applyFilterId(String filterId) {
        if (filterId == null || filterId.isBlank() || FILTER_ALL.equalsIgnoreCase(filterId)) {
            showAllCategories = true;
            selectedCategory = ModuleCategory.COMBAT;
            return;
        }
        for (ModuleCategory category : ModuleCategory.values()) {
            if (category.name().equalsIgnoreCase(filterId)) {
                showAllCategories = false;
                selectedCategory = category;
                return;
            }
        }
        showAllCategories = true;
        selectedCategory = ModuleCategory.COMBAT;
    }

    private String currentFilterId() {
        return showAllCategories ? FILTER_ALL : selectedCategory.name().toLowerCase(Locale.ROOT);
    }

    private boolean isSearchActive() {
        return !searchQuery.isBlank();
    }

    private List<Module> filteredModules() {
        String query = searchQuery.trim().toLowerCase(Locale.ROOT);
        List<Module> source = showAllCategories
                ? runtime.moduleManager().all().stream()
                .sorted(Comparator.comparingInt((Module module) -> module.category().ordinal()).thenComparing(Module::name, String.CASE_INSENSITIVE_ORDER))
                .toList()
                : runtime.moduleManager().byCategory(selectedCategory);
        if (query.isEmpty()) {
            return source;
        }
        return runtime.moduleManager().all().stream()
                .sorted(Comparator.comparingInt((Module module) -> module.category().ordinal()).thenComparing(Module::name, String.CASE_INSENSITIVE_ORDER))
                .filter(module -> module.name().toLowerCase(Locale.ROOT).contains(query)
                        || module.description().toLowerCase(Locale.ROOT).contains(query)
                        || module.settings().stream().anyMatch(setting -> setting.label().toLowerCase(Locale.ROOT).contains(query)))
                .toList();
    }

    private List<Setting<?>> visibleSettings(Module module) {
        return module.settings().stream()
                .filter(Setting::visible)
                .sorted((left, right) -> {
                    if (left instanceof KeyBindSetting && !(right instanceof KeyBindSetting)) {
                        return 1;
                    }
                    if (right instanceof KeyBindSetting && !(left instanceof KeyBindSetting)) {
                        return -1;
                    }
                    return 0;
                })
                .toList();
    }

    private Optional<Setting<?>> resolveSetting(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        int separator = path.indexOf(':');
        if (separator <= 0 || separator >= path.length() - 1) {
            return Optional.empty();
        }
        String moduleId = path.substring(0, separator);
        String settingId = path.substring(separator + 1);
        return runtime.moduleManager().getById(moduleId)
                .flatMap(module -> module.settings().stream().filter(setting -> setting.id().equals(settingId)).findFirst());
    }

    private List<String> wrapLines(String text, int maxWidth, ClickFont font) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (textWidth(candidate, font) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                }
                current.setLength(0);
                current.append(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private String trimToWidth(String text, int maxWidth, ClickFont font) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (textWidth(text, font) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth = textWidth(ellipsis, font);
        StringBuilder builder = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = builder.isEmpty() ? word : builder + " " + word;
            if (textWidth(candidate, font) + ellipsisWidth > maxWidth) {
                break;
            }
            builder.setLength(0);
            builder.append(candidate);
        }
        if (!builder.isEmpty()) {
            return builder + ellipsis;
        }

        StringBuilder clipped = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            String candidate = clipped.toString() + text.charAt(index);
            if (textWidth(candidate, font) + ellipsisWidth > maxWidth) {
                break;
            }
            clipped.append(text.charAt(index));
        }
        return clipped + ellipsis;
    }

    private int centeredTextY(ClickGuiLayout.Rect rect, ClickFont font) {
        return rect.y() + Math.max(2, (rect.height() - textLineHeight(font)) / 2);
    }

    private void drawStyledText(DrawContext drawContext, String value, int x, int y, int color, ClickFont font) {
        drawContext.drawText(textRenderer, styledText(value, font), x, y, color, false);
    }

    private int textWidth(String value, ClickFont font) {
        return textRenderer.getWidth(styledText(value, font));
    }

    private int textLineHeight(ClickFont font) {
        return switch (font) {
            case TITLE -> 16;
            case BODY, THIN -> 10;
            case SMALL -> 9;
        };
    }

    private Text styledText(String value, ClickFont font) {
        return switch (font) {
            case TITLE -> SmokeFonts.venomTitle(value);
            case SMALL -> SmokeFonts.venomSmall(value);
            case THIN -> SmokeFonts.venomThin(value);
            case BODY -> SmokeFonts.venom(value);
        };
    }

    private static String settingPath(Module module, Setting<?> setting) {
        return module.id() + ":" + setting.id();
    }

    private static double normalized(NumberSetting setting) {
        double range = setting.max() - setting.min();
        return range <= 0.0D ? 0.0D : (setting.value() - setting.min()) / range;
    }

    private List<SettingRow> visibleSettingRows(Module module) {
        List<Setting<?>> settings = visibleSettings(module);
        List<SettingRow> rows = new ArrayList<>(settings.size());
        for (int index = 0; index < settings.size(); index++) {
            Setting<?> setting = settings.get(index);
            if (setting instanceof NumberSetting minSetting
                    && index + 1 < settings.size()
                    && settings.get(index + 1) instanceof NumberSetting maxSetting
                    && isRangePair(minSetting, maxSetting)) {
                rows.add(new NumberRangeRow(minSetting, maxSetting, rangeLabel(minSetting, maxSetting)));
                index++;
                continue;
            }
            rows.add(new SingleSettingRow(setting));
        }
        return rows;
    }

    private static boolean isRangePair(NumberSetting minSetting, NumberSetting maxSetting) {
        String minSuffix = rangeSuffix(minSetting.id(), "min_");
        return minSuffix != null
                && minSuffix.equals(rangeSuffix(maxSetting.id(), "max_"))
                && Double.compare(minSetting.min(), maxSetting.min()) == 0
                && Double.compare(minSetting.max(), maxSetting.max()) == 0
                && Double.compare(minSetting.step(), maxSetting.step()) == 0;
    }

    private static String rangeSuffix(String id, String prefix) {
        return id.startsWith(prefix) && id.length() > prefix.length() ? id.substring(prefix.length()) : null;
    }

    private static String rangeLabel(NumberSetting minSetting, NumberSetting maxSetting) {
        String minLabel = stripRangeQualifier(minSetting.label());
        String maxLabel = stripRangeQualifier(maxSetting.label());
        if (!minLabel.isBlank() && minLabel.equalsIgnoreCase(maxLabel)) {
            return minLabel;
        }
        String suffix = rangeSuffix(minSetting.id(), "min_");
        return suffix == null ? minSetting.label() + " / " + maxSetting.label() : humanizeSettingId(suffix);
    }

    private static String stripRangeQualifier(String label) {
        return label.replaceAll("(?i)\\b(?:min|max)\\b", "").replaceAll("\\s{2,}", " ").trim();
    }

    private static String humanizeSettingId(String id) {
        StringBuilder label = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isBlank()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(part.length() <= 3
                    ? part.toUpperCase(Locale.ROOT)
                    : Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return label.isEmpty() ? id : label.toString();
    }

    private RangeHandle closestHandle(ClickGuiLayout.Rect rect, NumberRangeRow row, double mouseX) {
        int minX = sliderPosition(rect, normalized(row.minSetting()));
        int maxX = sliderPosition(rect, normalized(row.maxSetting()));
        return Math.abs(mouseX - minX) <= Math.abs(mouseX - maxX) ? RangeHandle.MIN : RangeHandle.MAX;
    }

    private interface DragTarget {
        void update(double mouseX);
    }

    private enum ClickFont {
        TITLE,
        BODY,
        SMALL,
        THIN
    }

    private sealed interface SettingRow permits SingleSettingRow, NumberRangeRow {
    }

    private record SingleSettingRow(Setting<?> setting) implements SettingRow {
    }

    private record NumberRangeRow(NumberSetting minSetting, NumberSetting maxSetting, String label) implements SettingRow {
    }

    private record NumberDrag(ClickGuiLayout.Rect rect, NumberSetting setting) implements DragTarget {
        @Override
        public void update(double mouseX) {
            double normalized = (mouseX - rect.x()) / rect.width();
            double value = setting.min() + ((setting.max() - setting.min()) * Math.max(0.0D, Math.min(1.0D, normalized)));
            setting.setValue(value);
        }
    }

    private record NumberRangeDrag(ClickGuiLayout.Rect rect, NumberSetting minSetting, NumberSetting maxSetting, RangeHandle handle) implements DragTarget {
        @Override
        public void update(double mouseX) {
            double normalized = (mouseX - rect.x()) / rect.width();
            double value = minSetting.min() + ((minSetting.max() - minSetting.min()) * Math.max(0.0D, Math.min(1.0D, normalized)));
            if (handle == RangeHandle.MIN) {
                minSetting.setValue(Math.min(value, maxSetting.value()));
            } else {
                maxSetting.setValue(Math.max(value, minSetting.value()));
            }
        }
    }

    private record ColorDrag(ClickGuiLayout.Rect rect, ColorSetting setting, ColorChannel channel) implements DragTarget {
        @Override
        public void update(double mouseX) {
            double normalized = (mouseX - rect.x()) / rect.width();
            int value = MathHelper.clamp((int) Math.round(Math.max(0.0D, Math.min(1.0D, normalized)) * 255.0D), 0, 255);
            setting.setValue(channel.apply(setting.value(), value));
        }
    }

    private record FilterSpec(String filterId, String label) {
    }

    private record FilterChip(String filterId, String label, ClickGuiLayout.Rect rect) {
    }

    private record SearchView(String visibleText, int caretOffset) {
    }

    private enum RangeHandle {
        MIN,
        MAX
    }

    private enum ColorChannel {
        ALPHA("A", 0xFFB8B0B0) {
            @Override
            int value(int argb) {
                return (argb >>> 24) & 0xFF;
            }

            @Override
            int apply(int argb, int value) {
                return (value << 24) | (argb & 0x00FFFFFF);
            }
        },
        RED("R", 0xFFC75D5D) {
            @Override
            int value(int argb) {
                return (argb >>> 16) & 0xFF;
            }

            @Override
            int apply(int argb, int value) {
                return (argb & 0xFF00FFFF) | (value << 16);
            }
        },
        GREEN("G", 0xFF67B86E) {
            @Override
            int value(int argb) {
                return (argb >>> 8) & 0xFF;
            }

            @Override
            int apply(int argb, int value) {
                return (argb & 0xFFFF00FF) | (value << 8);
            }
        },
        BLUE("B", 0xFF5F8FE0) {
            @Override
            int value(int argb) {
                return argb & 0xFF;
            }

            @Override
            int apply(int argb, int value) {
                return (argb & 0xFFFFFF00) | value;
            }
        };

        private final String label;
        private final int color;

        ColorChannel(String label, int color) {
            this.label = label;
            this.color = color;
        }

        abstract int value(int argb);

        abstract int apply(int argb, int value);
    }
}
