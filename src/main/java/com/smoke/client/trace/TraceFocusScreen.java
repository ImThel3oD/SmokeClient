package com.smoke.client.trace;

import com.smoke.client.bootstrap.ClientRuntime;
import com.smoke.client.input.BlocksModuleKeybindsScreen;
import com.smoke.client.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class TraceFocusScreen extends Screen implements BlocksModuleKeybindsScreen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 340;
    private static final int OUTER_MARGIN = 18;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SEARCH_HEIGHT = 20;
    private static final int ROW_HEIGHT = 26;
    private static final int ACCENT = 0xFF78C6FF;
    private static final int OVERLAY = 0x7A0B0F14;
    private static final int PANEL = 0xF0191E25;
    private static final int SURFACE = 0xFF202732;
    private static final int SURFACE_ALT = 0xFF28313D;
    private static final int BORDER = 0xFF3B4656;
    private static final int TEXT = 0xFFF3F1EC;
    private static final int TEXT_MUTED = 0xFF9AA3AF;
    private static final int TEXT_DIM = 0xFF748090;
    private static final int SELECTED = 0x334DA6FF;
    private static final int ENABLED = 0xFF7CFF9A;

    private final ClientRuntime runtime;
    private final Screen parent;
    private final LinkedHashSet<String> selectedModuleIds = new LinkedHashSet<>();

    private TextFieldWidget searchField;
    private double listScroll;
    private double maxScroll;

    private Rect panelRect = new Rect(0, 0, 0, 0);
    private Rect searchRect = new Rect(0, 0, 0, 0);
    private Rect listRect = new Rect(0, 0, 0, 0);
    private Rect autoButton = new Rect(0, 0, 0, 0);
    private Rect systemButton = new Rect(0, 0, 0, 0);
    private Rect clearButton = new Rect(0, 0, 0, 0);
    private Rect cancelButton = new Rect(0, 0, 0, 0);
    private Rect applyButton = new Rect(0, 0, 0, 0);

    public TraceFocusScreen(ClientRuntime runtime, Screen parent) {
        super(Text.literal("Trace Focus"));
        this.runtime = runtime;
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildLayout();

        String query = searchField == null ? "" : searchField.getText();
        searchField = new TextFieldWidget(
                textRenderer,
                searchRect.x() + 8,
                searchRect.y() + 6,
                searchRect.width() - 16,
                SEARCH_HEIGHT,
                Text.literal("Search trace modules")
        );
        searchField.setDrawsBackground(false);
        searchField.setMaxLength(48);
        searchField.setSuggestion("Search modules");
        searchField.setText(query);
        searchField.setChangedListener(value -> listScroll = 0.0D);
        searchField.setFocused(true);

        selectedModuleIds.clear();
        if (!runtime.moduleTraceService().autoFocus()) {
            for (String scopeId : runtime.moduleTraceService().focusedScopeIds()) {
                runtime.moduleManager().getById(scopeId).ifPresent(module -> selectedModuleIds.add(module.id()));
            }
        }
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        rebuildLayout();

        List<Module> filteredModules = filteredModules();
        int totalHeight = filteredModules.size() * ROW_HEIGHT;
        maxScroll = Math.max(0.0D, totalHeight - (listRect.height() - 4.0D));
        listScroll = MathHelper.clamp((float) listScroll, 0.0F, (float) maxScroll);

        drawContext.fill(0, 0, width, height, OVERLAY);
        drawPanel(drawContext, panelRect, PANEL, BORDER);
        drawPanel(drawContext, searchRect, SURFACE_ALT, searchField != null && searchField.isFocused() ? ACCENT : BORDER);
        drawPanel(drawContext, listRect, SURFACE, BORDER);
        drawContext.fill(panelRect.x(), panelRect.y(), panelRect.right(), panelRect.y() + 2, ACCENT);

        drawContext.drawText(textRenderer, "Trace Focus", panelRect.x() + 12, panelRect.y() + 10, TEXT, false);
        drawContext.drawText(textRenderer, "Search modules and click to toggle multiple traces.", panelRect.x() + 12, panelRect.y() + 22, TEXT_MUTED, false);

        String selectedLabel = selectedSummary();
        drawContext.drawText(textRenderer, selectedLabel, panelRect.x() + 12, panelRect.y() + 38, TEXT_DIM, false);

        if (searchField != null) {
            searchField.setX(searchRect.x() + 8);
            searchField.setY(searchRect.y() + 6);
            searchField.setWidth(searchRect.width() - 16);
            searchField.render(drawContext, mouseX, mouseY, delta);
        }

        renderModuleList(drawContext, filteredModules, mouseX, mouseY);
        renderButtons(drawContext, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchField != null && searchField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (autoButton.contains(mouseX, mouseY)) {
            runtime.moduleTraceService().focusAuto();
            close();
            return true;
        }
        if (systemButton.contains(mouseX, mouseY)) {
            runtime.moduleTraceService().focusSystem();
            close();
            return true;
        }
        if (clearButton.contains(mouseX, mouseY)) {
            selectedModuleIds.clear();
            return true;
        }
        if (cancelButton.contains(mouseX, mouseY)) {
            close();
            return true;
        }
        if (applyButton.contains(mouseX, mouseY)) {
            applySelection();
            return true;
        }

        if (listRect.contains(mouseX, mouseY)) {
            Module module = moduleAt(mouseX, mouseY);
            if (module != null) {
                toggleModule(module.id());
                return true;
            }
        }

        if (searchField != null) {
            searchField.setFocused(false);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (listRect.contains(mouseX, mouseY) && maxScroll > 0.0D) {
            listScroll = MathHelper.clamp((float) (listScroll - (verticalAmount * 18.0D)), 0.0F, (float) maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return searchField != null && searchField.charTyped(chr, modifiers) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            applySelection();
            return true;
        }
        if (searchField != null && searchField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void applySelection() {
        if (selectedModuleIds.isEmpty()) {
            runtime.moduleTraceService().focusSystem();
        } else {
            runtime.moduleTraceService().focusModules(selectedModuleIds);
        }
        close();
    }

    private void toggleModule(String moduleId) {
        if (!selectedModuleIds.remove(moduleId)) {
            selectedModuleIds.add(moduleId);
        }
    }

    private Module moduleAt(double mouseX, double mouseY) {
        List<Module> modules = filteredModules();
        int y = listRect.y() + 2 - (int) listScroll;
        for (Module module : modules) {
            Rect row = new Rect(listRect.x() + 2, y, listRect.width() - 4, ROW_HEIGHT - 2);
            if (row.contains(mouseX, mouseY)) {
                return module;
            }
            y += ROW_HEIGHT;
        }
        return null;
    }

    private void renderModuleList(DrawContext drawContext, List<Module> modules, int mouseX, int mouseY) {
        if (modules.isEmpty()) {
            String text = searchField != null && !searchField.getText().isBlank()
                    ? "No modules match that search."
                    : "No modules available.";
            int textWidth = textRenderer.getWidth(text);
            drawContext.drawText(textRenderer, text, listRect.x() + listRect.width() / 2 - textWidth / 2, listRect.y() + listRect.height() / 2 - 4, TEXT_DIM, false);
            return;
        }

        drawContext.enableScissor(listRect.x(), listRect.y(), listRect.right(), listRect.bottom());
        int y = listRect.y() + 2 - (int) listScroll;
        for (Module module : modules) {
            Rect row = new Rect(listRect.x() + 2, y, listRect.width() - 4, ROW_HEIGHT - 2);
            boolean hovered = row.contains(mouseX, mouseY);
            boolean selected = selectedModuleIds.contains(module.id());
            int fillColor = selected ? SELECTED : hovered ? SURFACE_ALT : SURFACE;
            drawContext.fill(row.x(), row.y(), row.right(), row.bottom(), fillColor);
            drawContext.fill(row.x(), row.y(), row.x() + 2, row.bottom(), selected ? ACCENT : BORDER);

            String line = module.name();
            String meta = module.id() + " - " + module.category().name().toLowerCase(Locale.ROOT);
            drawContext.drawText(textRenderer, line, row.x() + 8, row.y() + 5, TEXT, false);
            drawContext.drawText(textRenderer, meta, row.x() + 8, row.y() + 15, TEXT_MUTED, false);
            if (module.enabled()) {
                drawContext.drawText(textRenderer, "ON", row.right() - 18, row.y() + 9, ENABLED, false);
            }
            y += ROW_HEIGHT;
        }
        drawContext.disableScissor();
    }

    private void renderButtons(DrawContext drawContext, int mouseX, int mouseY) {
        drawButton(drawContext, autoButton, mouseX, mouseY, "Auto", 0xFF3D5065, true);
        drawButton(drawContext, systemButton, mouseX, mouseY, "System", 0xFF3D5065, true);
        drawButton(drawContext, clearButton, mouseX, mouseY, "Clear", 0xFF4A4A4A, true);
        drawButton(drawContext, cancelButton, mouseX, mouseY, "Cancel", 0xFF4A4A4A, true);
        drawButton(drawContext, applyButton, mouseX, mouseY, selectedModuleIds.isEmpty() ? "Apply System" : "Apply", ACCENT, true);
    }

    private void drawButton(DrawContext drawContext, Rect rect, int mouseX, int mouseY, String label, int fillColor, boolean enabled) {
        boolean hovered = rect.contains(mouseX, mouseY);
        int background = enabled
                ? (hovered ? lighten(fillColor) : fillColor)
                : 0xFF30353D;
        drawContext.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), background);
        drawContext.fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, BORDER);
        drawContext.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), BORDER);
        drawContext.fill(rect.x(), rect.y(), rect.x() + 1, rect.bottom(), BORDER);
        drawContext.fill(rect.right() - 1, rect.y(), rect.right(), rect.bottom(), BORDER);

        int labelWidth = textRenderer.getWidth(label);
        drawContext.drawText(
                textRenderer,
                label,
                rect.x() + rect.width() / 2 - labelWidth / 2,
                rect.y() + 6,
                enabled ? TEXT : TEXT_DIM,
                false
        );
    }

    private void drawPanel(DrawContext drawContext, Rect rect, int fillColor, int borderColor) {
        drawContext.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fillColor);
        drawContext.fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, borderColor);
        drawContext.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), borderColor);
        drawContext.fill(rect.x(), rect.y(), rect.x() + 1, rect.bottom(), borderColor);
        drawContext.fill(rect.right() - 1, rect.y(), rect.right(), rect.bottom(), borderColor);
    }

    private List<Module> filteredModules() {
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        return runtime.moduleManager().all().stream()
                .sorted(Comparator.comparing(Module::name, String.CASE_INSENSITIVE_ORDER))
                .filter(module -> query.isBlank()
                        || module.name().toLowerCase(Locale.ROOT).contains(query)
                        || module.id().contains(query))
                .toList();
    }

    private String selectedSummary() {
        if (selectedModuleIds.isEmpty()) {
            return "Selected: system only";
        }

        List<String> names = new ArrayList<>();
        for (String moduleId : selectedModuleIds) {
            runtime.moduleManager().getById(moduleId).ifPresent(module -> names.add(module.name()));
        }
        if (names.isEmpty()) {
            return "Selected: system only";
        }
        if (names.size() == 1) {
            return "Selected: " + names.get(0) + " + system";
        }
        if (names.size() == 2) {
            return "Selected: " + names.get(0) + ", " + names.get(1) + " + system";
        }
        return "Selected: " + names.get(0) + " + " + (names.size() - 1) + " more + system";
    }

    private void rebuildLayout() {
        int panelWidth = Math.min(PANEL_WIDTH, width - OUTER_MARGIN * 2);
        int panelHeight = Math.min(PANEL_HEIGHT, height - OUTER_MARGIN * 2);
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;
        panelRect = new Rect(panelX, panelY, panelWidth, panelHeight);

        searchRect = new Rect(panelX + 12, panelY + 54, panelWidth - 24, 32);
        listRect = new Rect(panelX + 12, searchRect.bottom() + 8, panelWidth - 24, panelHeight - 132);

        int buttonY = panelRect.bottom() - 28;
        int gap = 6;
        int buttonWidth = (panelWidth - 24 - gap * 4) / 5;
        autoButton = new Rect(panelX + 12, buttonY, buttonWidth, BUTTON_HEIGHT);
        systemButton = new Rect(autoButton.right() + gap, buttonY, buttonWidth, BUTTON_HEIGHT);
        clearButton = new Rect(systemButton.right() + gap, buttonY, buttonWidth, BUTTON_HEIGHT);
        cancelButton = new Rect(clearButton.right() + gap, buttonY, buttonWidth, BUTTON_HEIGHT);
        applyButton = new Rect(cancelButton.right() + gap, buttonY, buttonWidth, BUTTON_HEIGHT);
    }

    private static int lighten(int color) {
        int a = color >>> 24;
        int r = Math.min(255, ((color >>> 16) & 0xFF) + 16);
        int g = Math.min(255, ((color >>> 8) & 0xFF) + 16);
        int b = Math.min(255, (color & 0xFF) + 16);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private record Rect(int x, int y, int width, int height) {
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
