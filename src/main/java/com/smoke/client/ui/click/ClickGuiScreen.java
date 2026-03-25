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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ClickGuiScreen extends Screen implements BlocksModuleKeybindsScreen {
    private static final String UI_SECTION = "click_gui";
    private static final String FILTER_KEY = "selected_filter";
    private static final String FILTER_ALL = "all";

    private final ClientRuntime runtime;

    private TextFieldWidget searchField;
    private ClickGuiLayout layout;
    private ModuleCategory selectedCategory = ModuleCategory.COMBAT;
    private boolean showAllCategories = true;
    private String expandedModuleId;
    private String openColorSettingPath;
    private String pendingKeybindPath;
    private DragTarget activeDrag;
    private double listScroll;
    private double maxScroll;

    public ClickGuiScreen(ClientRuntime runtime) {
        super(Text.literal("Smoke Click GUI"));
        this.runtime = runtime;
        restoreFilter();
    }

    @Override
    protected void init() {
        layout = ClickGuiLayout.create(width, height);
        String query = searchField == null ? "" : searchField.getText();
        searchField = new TextFieldWidget(
                textRenderer,
                layout.search().x() + 10,
                layout.search().y() + 9,
                layout.search().width() - 20,
                ClickGuiPalette.SEARCH_HEIGHT - 2,
                Text.literal("Search modules")
        );
        searchField.setDrawsBackground(false);
        searchField.setMaxLength(48);
        searchField.setSuggestion("Search modules");
        searchField.setText(query);
        searchField.setChangedListener(value -> {
            listScroll = 0.0D;
            if (expandedModuleId != null && filteredModules().stream().noneMatch(module -> module.id().equals(expandedModuleId))) {
                expandedModuleId = null;
                openColorSettingPath = null;
                pendingKeybindPath = null;
                activeDrag = null;
            }
        });
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        layout = ClickGuiLayout.create(width, height);
        List<Module> modules = filteredModules();
        if (expandedModuleId != null && modules.stream().noneMatch(module -> module.id().equals(expandedModuleId))) {
            expandedModuleId = null;
            openColorSettingPath = null;
            pendingKeybindPath = null;
            activeDrag = null;
        }

        int scrollbarWidth = modules.isEmpty() ? 0 : 6;
        int contentWidth = layout.list().width() - 12 - scrollbarWidth;
        int totalHeight = measureModuleListHeight(modules, contentWidth);
        maxScroll = Math.max(0.0D, totalHeight - (layout.list().height() - 12.0D));
        listScroll = MathHelper.clamp((float) listScroll, 0.0F, (float) maxScroll);

        drawContext.fill(0, 0, width, height, ClickGuiPalette.OVERLAY);
        drawPanel(drawContext, layout.window(), ClickGuiPalette.WINDOW, ClickGuiPalette.BORDER);
        drawPanel(drawContext, layout.sidebar(), ClickGuiPalette.SURFACE, ClickGuiPalette.BORDER);
        drawPanel(drawContext, layout.content(), ClickGuiPalette.SURFACE, ClickGuiPalette.BORDER);
        drawPanel(drawContext, layout.search(), ClickGuiPalette.SURFACE_ALT, searchField != null && searchField.isFocused() ? ClickGuiPalette.ACCENT : ClickGuiPalette.BORDER);
        drawPanel(drawContext, layout.list(), ClickGuiPalette.SURFACE_ALT, ClickGuiPalette.BORDER);

        drawContext.drawText(textRenderer, "Smoke", layout.sidebar().x() + 10, layout.sidebar().y() + 8, ClickGuiPalette.TEXT_PRIMARY, false);
        drawContext.drawText(textRenderer, "Client settings", layout.sidebar().x() + 10, layout.sidebar().y() + 18, ClickGuiPalette.TEXT_MUTED, false);
        drawContext.drawText(textRenderer, "Modules", layout.content().x(), layout.contentHeaderY(), ClickGuiPalette.TEXT_PRIMARY, false);

        String resultLabel = modules.size() + (modules.size() == 1 ? " result" : " results");
        drawContext.drawText(
                textRenderer,
                resultLabel,
                layout.content().right() - textRenderer.getWidth(resultLabel),
                layout.contentHeaderY(),
                ClickGuiPalette.TEXT_MUTED,
                false
        );

        renderCategories(drawContext, mouseX, mouseY);
        if (searchField != null) {
            searchField.setX(layout.search().x() + 10);
            searchField.setY(layout.search().y() + 9);
            searchField.setWidth(layout.search().width() - 20);
            searchField.render(drawContext, mouseX, mouseY, delta);
        }
        renderModuleList(drawContext, modules, mouseX, mouseY, contentWidth, scrollbarWidth);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchField != null && searchField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && handleCategoryClick(mouseX, mouseY)) {
            searchField.setFocused(false);
            return true;
        }
        if (button == 0 && handleModuleClick(mouseX, mouseY)) {
            searchField.setFocused(false);
            return true;
        }
        if (button == 0) {
            searchField.setFocused(false);
            openColorSettingPath = null;
            pendingKeybindPath = null;
            activeDrag = null;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (activeDrag != null && button == 0) {
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
        if (layout != null && layout.list().contains(mouseX, mouseY) && maxScroll > 0.0D) {
            listScroll = MathHelper.clamp((float) (listScroll - (verticalAmount * 18.0D)), 0.0F, (float) maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (pendingKeybindPath != null) {
            return true;
        }
        return searchField != null && searchField.charTyped(chr, modifiers) || super.charTyped(chr, modifiers);
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

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (searchField != null && searchField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        runtime.configService().putUiValue(UI_SECTION, FILTER_KEY, new JsonPrimitive(currentFilterId()));
        runtime.configService().save(runtime.moduleManager());
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean allowClientHotkey(int keyCode) {
        return pendingKeybindPath == null;
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

    private void renderCategories(DrawContext drawContext, int mouseX, int mouseY) {
        int rowY = layout.sidebarHeaderBottom();
        rowY = renderCategoryRow(drawContext, mouseX, mouseY, rowY, null, "All", showAllCategories);
        for (ModuleCategory category : ModuleCategory.values()) {
            rowY = renderCategoryRow(drawContext, mouseX, mouseY, rowY, category, category.displayName(), !showAllCategories && selectedCategory == category);
        }
    }

    private void renderModuleList(DrawContext drawContext, List<Module> modules, int mouseX, int mouseY, int contentWidth, int scrollbarWidth) {
        if (modules.isEmpty()) {
            String label = searchField != null && !searchField.getText().isBlank()
                    ? "No modules match that search."
                    : "No modules in this category yet.";
            drawContext.drawText(
                    textRenderer,
                    label,
                    layout.list().x() + (layout.list().width() - textRenderer.getWidth(label)) / 2,
                    layout.list().y() + (layout.list().height() / 2) - 4,
                    ClickGuiPalette.TEXT_MUTED,
                    false
            );
            return;
        }

        drawContext.enableScissor(layout.list().x(), layout.list().y(), layout.list().right(), layout.list().bottom());
        int rowX = layout.list().x() + 6;
        int rowY = layout.list().y() + 6 - (int) Math.round(listScroll);

        for (Module module : modules) {
            ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(rowX, rowY, contentWidth, ClickGuiPalette.MODULE_ROW_HEIGHT);
            boolean expanded = module.id().equals(expandedModuleId);
            boolean hovered = rowRect.contains(mouseX, mouseY);
            int fill = expanded ? ClickGuiPalette.SURFACE_ACTIVE : hovered ? ClickGuiPalette.SURFACE : ClickGuiPalette.SURFACE_ALT;
            drawPanel(drawContext, rowRect, fill, expanded ? ClickGuiPalette.ACCENT : ClickGuiPalette.BORDER);
            if (module.enabled()) {
                drawContext.fill(rowRect.x(), rowRect.y(), rowRect.x() + 2, rowRect.bottom(), ClickGuiPalette.ACCENT);
            }
            drawContext.drawText(
                    textRenderer,
                    module.name(),
                    rowRect.x() + 10,
                    rowRect.y() + 10,
                    module.enabled() ? ClickGuiPalette.TEXT_PRIMARY : ClickGuiPalette.TEXT_SECONDARY,
                    false
            );

            ClickGuiLayout.Rect toggleRect = toggleRect(rowRect);
            renderToggle(drawContext, toggleRect, module.enabled());
            if (showAllCategories || isSearchActive()) {
                String category = module.category().displayName();
                int categoryWidth = textRenderer.getWidth(category);
                int categoryX = toggleRect.x() - 10 - categoryWidth;
                if (categoryX > rowRect.x() + 60) {
                    drawContext.drawText(textRenderer, category, categoryX, rowRect.y() + 10, ClickGuiPalette.TEXT_MUTED, false);
                }
            }

            rowY += ClickGuiPalette.MODULE_ROW_HEIGHT + 2;
            if (expanded) {
                rowY = renderExpandedModule(drawContext, module, rowX, rowY, contentWidth) + 6;
            } else {
                rowY += 4;
            }
        }
        drawContext.disableScissor();

        if (maxScroll > 0.0D) {
            int trackX = layout.list().right() - scrollbarWidth;
            int trackHeight = layout.list().height() - 12;
            int thumbHeight = Math.max(24, (int) Math.round(trackHeight * (trackHeight / (double) (trackHeight + maxScroll))));
            int thumbY = layout.list().y() + 6 + (int) Math.round((trackHeight - thumbHeight) * (listScroll / maxScroll));
            drawContext.fill(trackX, layout.list().y() + 6, trackX + 2, layout.list().bottom() - 6, ClickGuiPalette.BORDER);
            drawContext.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, ClickGuiPalette.ACCENT);
        }
    }

    private int measureModuleListHeight(List<Module> modules, int width) {
        int total = 6;
        for (Module module : modules) {
            total += ClickGuiPalette.MODULE_ROW_HEIGHT + 6;
            if (module.id().equals(expandedModuleId)) {
                total += measureExpandedHeight(module, width) + 2;
            }
        }
        return total;
    }

    private int measureExpandedHeight(Module module, int width) {
        int total = 10;
        total += wrapLines(module.description(), Math.max(40, width - 20)).size() * (textRenderer.fontHeight + 2);
        String suffix = module.displaySuffix();
        total += suffix == null || suffix.isBlank() ? 6 : textRenderer.fontHeight + 8;
        for (Setting<?> setting : visibleSettings(module)) {
            total += switch (setting) {
                case NumberSetting ignored -> ClickGuiPalette.NUMBER_ROW_HEIGHT;
                case ColorSetting colorSetting -> ClickGuiPalette.SETTING_ROW_HEIGHT
                        + (settingPath(module, colorSetting).equals(openColorSettingPath) ? 6 + (ClickGuiPalette.COLOR_CHANNEL_HEIGHT * 4) + 12 : 0);
                default -> ClickGuiPalette.SETTING_ROW_HEIGHT;
            };
            total += 6;
        }
        return total + 4;
    }

    private List<Module> filteredModules() {
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
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

    private int renderCategoryRow(
            DrawContext drawContext,
            int mouseX,
            int mouseY,
            int rowY,
            ModuleCategory category,
            String label,
            boolean selected
    ) {
        ClickGuiLayout.Rect rect = new ClickGuiLayout.Rect(
                layout.sidebar().x() + 8,
                rowY,
                layout.sidebar().width() - 16,
                ClickGuiPalette.CATEGORY_ROW_HEIGHT
        );
        boolean hovered = rect.contains(mouseX, mouseY);
        int fill = selected ? ClickGuiPalette.ACCENT_SOFT : hovered ? ClickGuiPalette.SURFACE_ACTIVE : ClickGuiPalette.SURFACE_ALT;
        int textColor = selected ? ClickGuiPalette.TEXT_PRIMARY : hovered ? ClickGuiPalette.TEXT_PRIMARY : ClickGuiPalette.TEXT_SECONDARY;
        drawPanel(drawContext, rect, fill, selected ? ClickGuiPalette.ACCENT : ClickGuiPalette.BORDER);
        if (selected) {
            drawContext.fill(rect.x(), rect.y(), rect.x() + 2, rect.bottom(), ClickGuiPalette.ACCENT);
        }
        drawContext.drawText(textRenderer, label, rect.x() + 8, rect.y() + 7, textColor, false);
        return rowY + ClickGuiPalette.CATEGORY_ROW_HEIGHT + 4;
    }

    private ClickGuiLayout.Rect toggleRect(ClickGuiLayout.Rect rowRect) {
        return new ClickGuiLayout.Rect(rowRect.right() - 42, rowRect.y() + 6, 34, 16);
    }

    private int renderExpandedModule(DrawContext drawContext, Module module, int x, int y, int width) {
        int bodyHeight = measureExpandedHeight(module, width);
        ClickGuiLayout.Rect bodyRect = new ClickGuiLayout.Rect(x, y, width, bodyHeight);
        drawPanel(drawContext, bodyRect, ClickGuiPalette.SURFACE, ClickGuiPalette.BORDER);

        int cursorY = bodyRect.y() + 10;
        int contentX = bodyRect.x() + 10;
        int contentWidth = bodyRect.width() - 20;
        for (String line : wrapLines(module.description(), contentWidth)) {
            drawContext.drawText(textRenderer, line, contentX, cursorY, ClickGuiPalette.TEXT_MUTED, false);
            cursorY += textRenderer.fontHeight + 2;
        }

        String suffix = module.displaySuffix();
        if (suffix != null && !suffix.isBlank()) {
            cursorY += 2;
            drawContext.drawText(textRenderer, suffix, contentX, cursorY, ClickGuiPalette.TEXT_SECONDARY, false);
            cursorY += textRenderer.fontHeight + 6;
        } else {
            cursorY += 6;
        }

        for (Setting<?> setting : visibleSettings(module)) {
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
            cursorY += measureSettingHeight(module, setting) + 6;
        }
        return bodyRect.bottom();
    }

    private int measureSettingHeight(Module module, Setting<?> setting) {
        return switch (setting) {
            case NumberSetting ignored -> ClickGuiPalette.NUMBER_ROW_HEIGHT;
            case ColorSetting colorSetting -> ClickGuiPalette.SETTING_ROW_HEIGHT
                    + (settingPath(module, colorSetting).equals(openColorSettingPath) ? 6 + (ClickGuiPalette.COLOR_CHANNEL_HEIGHT * 4) + 12 : 0);
            default -> ClickGuiPalette.SETTING_ROW_HEIGHT;
        };
    }

    private void renderBoolSetting(DrawContext drawContext, BoolSetting setting, int x, int y, int width) {
        ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(x, y, width, ClickGuiPalette.SETTING_ROW_HEIGHT);
        drawContext.drawText(textRenderer, setting.label(), rowRect.x(), rowRect.y() + 8, ClickGuiPalette.TEXT_PRIMARY, false);
        renderToggle(drawContext, toggleRect(rowRect), setting.value());
    }

    private void renderNumberSetting(DrawContext drawContext, NumberSetting setting, int x, int y, int width) {
        ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(x, y, width, ClickGuiPalette.NUMBER_ROW_HEIGHT);
        String value = setting.displayValue();
        drawContext.drawText(textRenderer, setting.label(), rowRect.x(), rowRect.y(), ClickGuiPalette.TEXT_PRIMARY, false);
        drawContext.drawText(textRenderer, value, rowRect.right() - textRenderer.getWidth(value), rowRect.y(), ClickGuiPalette.TEXT_SECONDARY, false);
        renderSlider(drawContext, sliderRect(rowRect), normalized(setting), ClickGuiPalette.TRACK_FILL);
    }

    private void renderEnumSetting(DrawContext drawContext, EnumSetting<?> setting, int x, int y, int width) {
        ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(x, y, width, ClickGuiPalette.SETTING_ROW_HEIGHT);
        drawContext.drawText(textRenderer, setting.label(), rowRect.x(), rowRect.y() + 8, ClickGuiPalette.TEXT_PRIMARY, false);
        ClickGuiLayout.Rect buttonRect = actionRect(rowRect);
        drawPanel(drawContext, buttonRect, ClickGuiPalette.SURFACE_ALT, ClickGuiPalette.BORDER);
        String value = setting.displayValue();
        drawContext.drawText(textRenderer, "<", buttonRect.x() + 8, buttonRect.y() + 8, ClickGuiPalette.TEXT_MUTED, false);
        drawContext.drawText(
                textRenderer,
                value,
                buttonRect.x() + (buttonRect.width() - textRenderer.getWidth(value)) / 2,
                buttonRect.y() + 8,
                ClickGuiPalette.TEXT_PRIMARY,
                false
        );
        drawContext.drawText(textRenderer, ">", buttonRect.right() - 12, buttonRect.y() + 8, ClickGuiPalette.TEXT_MUTED, false);
    }

    private void renderColorSetting(DrawContext drawContext, Module module, ColorSetting setting, int x, int y, int width) {
        ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(x, y, width, ClickGuiPalette.SETTING_ROW_HEIGHT);
        drawContext.drawText(textRenderer, setting.label(), rowRect.x(), rowRect.y() + 8, ClickGuiPalette.TEXT_PRIMARY, false);
        ClickGuiLayout.Rect buttonRect = actionRect(rowRect);
        drawPanel(drawContext, buttonRect, ClickGuiPalette.SURFACE_ALT, ClickGuiPalette.BORDER);
        drawContext.fill(buttonRect.x() + 6, buttonRect.y() + 4, buttonRect.x() + 20, buttonRect.bottom() - 4, setting.value());
        drawContext.drawText(textRenderer, setting.displayValue(), buttonRect.x() + 26, buttonRect.y() + 8, ClickGuiPalette.TEXT_PRIMARY, false);

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
        String label = channel.label + " " + channel.value(setting.value());
        drawContext.drawText(textRenderer, label, rowRect.x(), rowRect.y(), ClickGuiPalette.TEXT_SECONDARY, false);
        renderSlider(drawContext, colorSliderRect(rowRect), channel.value(setting.value()) / 255.0D, channel.color);
    }

    private void renderKeybindSetting(DrawContext drawContext, Module module, KeyBindSetting setting, int x, int y, int width) {
        ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(x, y, width, ClickGuiPalette.SETTING_ROW_HEIGHT);
        drawContext.drawText(textRenderer, setting.label(), rowRect.x(), rowRect.y() + 8, ClickGuiPalette.TEXT_PRIMARY, false);
        ClickGuiLayout.Rect buttonRect = actionRect(rowRect);
        boolean pending = settingPath(module, setting).equals(pendingKeybindPath);
        drawPanel(drawContext, buttonRect, ClickGuiPalette.SURFACE_ALT, pending ? ClickGuiPalette.ACCENT : ClickGuiPalette.BORDER);
        String value = pending ? "Press a key" : setting.displayValue();
        drawContext.drawText(
                textRenderer,
                value,
                buttonRect.x() + (buttonRect.width() - textRenderer.getWidth(value)) / 2,
                buttonRect.y() + 8,
                pending ? ClickGuiPalette.TEXT_PRIMARY : ClickGuiPalette.TEXT_SECONDARY,
                false
        );
    }

    private boolean isSearchActive() {
        return searchField != null && !searchField.getText().isBlank();
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

    private boolean handleCategoryClick(double mouseX, double mouseY) {
        int rowY = layout.sidebarHeaderBottom();
        ClickGuiLayout.Rect rect = new ClickGuiLayout.Rect(layout.sidebar().x() + 8, rowY, layout.sidebar().width() - 16, ClickGuiPalette.CATEGORY_ROW_HEIGHT);
        if (rect.contains(mouseX, mouseY)) {
            showAllCategories = true;
            listScroll = 0.0D;
            expandedModuleId = null;
            openColorSettingPath = null;
            pendingKeybindPath = null;
            activeDrag = null;
            return true;
        }
        rowY += ClickGuiPalette.CATEGORY_ROW_HEIGHT + 4;
        for (ModuleCategory category : ModuleCategory.values()) {
            rect = new ClickGuiLayout.Rect(layout.sidebar().x() + 8, rowY, layout.sidebar().width() - 16, ClickGuiPalette.CATEGORY_ROW_HEIGHT);
            if (rect.contains(mouseX, mouseY)) {
                showAllCategories = false;
                selectedCategory = category;
                listScroll = 0.0D;
                expandedModuleId = null;
                openColorSettingPath = null;
                pendingKeybindPath = null;
                activeDrag = null;
                return true;
            }
            rowY += ClickGuiPalette.CATEGORY_ROW_HEIGHT + 4;
        }
        return false;
    }

    private boolean handleModuleClick(double mouseX, double mouseY) {
        List<Module> modules = filteredModules();
        int scrollbarWidth = modules.isEmpty() ? 0 : 6;
        int contentWidth = layout.list().width() - 12 - scrollbarWidth;
        int rowX = layout.list().x() + 6;
        int rowY = layout.list().y() + 6 - (int) Math.round(listScroll);
        for (Module module : modules) {
            ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(rowX, rowY, contentWidth, ClickGuiPalette.MODULE_ROW_HEIGHT);
            ClickGuiLayout.Rect toggleRect = toggleRect(rowRect);
            if (toggleRect.contains(mouseX, mouseY)) {
                runtime.moduleManager().toggle(module);
                return true;
            }
            if (rowRect.contains(mouseX, mouseY)) {
                if (module.id().equals(expandedModuleId)) {
                    expandedModuleId = null;
                    openColorSettingPath = null;
                    pendingKeybindPath = null;
                    activeDrag = null;
                } else {
                    expandedModuleId = module.id();
                    openColorSettingPath = null;
                    pendingKeybindPath = null;
                    activeDrag = null;
                }
                return true;
            }
            rowY += ClickGuiPalette.MODULE_ROW_HEIGHT + 2;
            if (module.id().equals(expandedModuleId)) {
                if (handleExpandedClick(module, mouseX, mouseY, rowX, rowY, contentWidth)) {
                    return true;
                }
                rowY += measureExpandedHeight(module, contentWidth) + 6;
            } else {
                rowY += 4;
            }
        }
        return false;
    }

    private boolean handleExpandedClick(Module module, double mouseX, double mouseY, int x, int y, int width) {
        int cursorY = y + 10;
        cursorY += wrapLines(module.description(), Math.max(40, width - 20)).size() * (textRenderer.fontHeight + 2);
        String suffix = module.displaySuffix();
        cursorY += suffix == null || suffix.isBlank() ? 6 : textRenderer.fontHeight + 8;

        for (Setting<?> setting : visibleSettings(module)) {
            ClickGuiLayout.Rect rowRect = new ClickGuiLayout.Rect(x + 10, cursorY, width - 20, measureSettingHeight(module, setting));
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
                    enumSetting.cycle(mouseX >= actionRect.x() + (actionRect.width() / 2.0D));
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
            cursorY += measureSettingHeight(module, setting) + 6;
        }
        return false;
    }

    private ClickGuiLayout.Rect actionRect(ClickGuiLayout.Rect rowRect) {
        int buttonWidth = Math.min(112, Math.max(84, rowRect.width() / 2));
        return new ClickGuiLayout.Rect(rowRect.right() - buttonWidth, rowRect.y(), buttonWidth, ClickGuiPalette.SETTING_ROW_HEIGHT);
    }

    private ClickGuiLayout.Rect sliderRect(ClickGuiLayout.Rect rowRect) {
        return new ClickGuiLayout.Rect(rowRect.x(), rowRect.y() + 18, rowRect.width(), 6);
    }

    private ClickGuiLayout.Rect colorSliderRect(ClickGuiLayout.Rect rowRect) {
        return new ClickGuiLayout.Rect(rowRect.x(), rowRect.y() + 14, rowRect.width(), 6);
    }

    private void renderToggle(DrawContext drawContext, ClickGuiLayout.Rect rect, boolean enabled) {
        int trackColor = enabled ? ClickGuiPalette.ACCENT : ClickGuiPalette.SWITCH_OFF;
        drawPanel(drawContext, rect, trackColor, enabled ? ClickGuiPalette.ACCENT : ClickGuiPalette.BORDER);
        int knobX = enabled ? rect.right() - 13 : rect.x() + 1;
        drawContext.fill(knobX, rect.y() + 1, knobX + 12, rect.bottom() - 1, ClickGuiPalette.SWITCH_KNOB);
    }

    private void renderSlider(DrawContext drawContext, ClickGuiLayout.Rect rect, double normalized, int fillColor) {
        drawContext.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), ClickGuiPalette.TRACK);
        int fillWidth = (int) Math.round(rect.width() * MathHelper.clamp((float) normalized, 0.0F, 1.0F));
        drawContext.fill(rect.x(), rect.y(), rect.x() + fillWidth, rect.bottom(), fillColor);
        int knobX = MathHelper.clamp(rect.x() + fillWidth - 3, rect.x(), rect.right() - 6);
        drawContext.fill(knobX, rect.y() - 1, knobX + 6, rect.bottom() + 1, ClickGuiPalette.SWITCH_KNOB);
    }

    private List<String> wrapLines(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (textRenderer.getWidth(candidate) <= maxWidth) {
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

    private void drawPanel(DrawContext drawContext, ClickGuiLayout.Rect rect, int fillColor, int borderColor) {
        drawContext.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fillColor);
        drawContext.fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, borderColor);
        drawContext.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), borderColor);
        drawContext.fill(rect.x(), rect.y(), rect.x() + 1, rect.bottom(), borderColor);
        drawContext.fill(rect.right() - 1, rect.y(), rect.right(), rect.bottom(), borderColor);
    }

    private static String settingPath(Module module, Setting<?> setting) {
        return module.id() + ":" + setting.id();
    }

    private static double normalized(NumberSetting setting) {
        double range = setting.max() - setting.min();
        return range <= 0.0D ? 0.0D : (setting.value() - setting.min()) / range;
    }

    private interface DragTarget {
        void update(double mouseX);
    }

    private record NumberDrag(ClickGuiLayout.Rect rect, NumberSetting setting) implements DragTarget {
        @Override
        public void update(double mouseX) {
            double normalized = (mouseX - rect.x()) / rect.width();
            double value = setting.min() + ((setting.max() - setting.min()) * Math.max(0.0D, Math.min(1.0D, normalized)));
            setting.setValue(value);
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
