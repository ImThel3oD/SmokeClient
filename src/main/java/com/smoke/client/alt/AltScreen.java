package com.smoke.client.alt;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public final class AltScreen extends Screen {
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_MARGIN = 22;
    private static final int ENTRY_HEIGHT = 30;
    private static final int BUTTON_HEIGHT = 20;
    private static final long DOUBLE_CLICK_MS = 350L;
    private static final int ACCENT = 0xFF4DA6FF;
    private static final int STATUS_ERROR = 0xFFFF6666;
    private static final int STATUS_INFO = 0xFFFFD166;
    private static final int STATUS_OK = 0xFF7CFF9A;
    private static final int TEXT_COLOR = 0xFFE6E6E6;
    private static final int TEXT_DIM = 0xFF9A9A9A;

    private final Screen parent;
    private TextFieldWidget apiKeyField;

    private final int[] generateButton = new int[4];
    private final int[] loginButton = new int[4];
    private final int[] restoreButton = new int[4];
    private final int[] deleteButton = new int[4];
    private final int[] backButton = new int[4];

    private String apiKey = "";
    private List<AltAccount> alts = new ArrayList<>();
    private final Set<String> errorAlts = ConcurrentHashMap.newKeySet();

    private int panelLeft;
    private int panelRight;
    private int panelTop;
    private int panelBottom;
    private int listTop;
    private int listBottom;
    private int scroll;
    private int maxScroll;
    private int selectedIndex = -1;
    private int lastClickedIndex = -1;
    private long lastClickTime;

    private String status = "";
    private int statusColor = TEXT_DIM;
    private long statusTime;
    private boolean loading;
    private boolean generateEnabled = true;

    public AltScreen(Screen parent) {
        super(Text.literal("Alt Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        AlteningService.AltData data = AlteningService.loadData();
        apiKey = data.apiKey;
        alts = new ArrayList<>(data.alts);

        panelLeft = this.width / 2 - PANEL_WIDTH / 2;
        panelRight = panelLeft + PANEL_WIDTH;
        panelTop = PANEL_MARGIN;
        panelBottom = this.height - PANEL_MARGIN;

        int apiY = panelTop + 38;
        int fieldWidth = PANEL_WIDTH - 180;
        apiKeyField = new TextFieldWidget(this.textRenderer, panelLeft + 95, apiY, fieldWidth, 18, Text.literal("API Key"));
        apiKeyField.setMaxLength(128);
        apiKeyField.setText(apiKey);
        addDrawableChild(apiKeyField);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {

    }

    @Override
    public void close() {
        saveData();
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    private void generateAlt() {
        if (loading) {
            return;
        }

        String key = apiKeyField.getText().trim();
        if (key.isEmpty()) {
            setStatus("Enter API key first.", STATUS_ERROR);
            return;
        }

        apiKey = key;
        loading = true;
        generateEnabled = false;
        setStatus("Generating alt...", STATUS_INFO);

        Thread worker = new Thread(() -> {
            try {
                AltAccount account = AlteningApiClient.generate(key);
                MinecraftClient.getInstance().execute(() -> {
                    alts.add(0, account);
                    selectedIndex = 0;
                    saveData();
                    setStatus("Logging in as " + account.getUsername() + "...", STATUS_INFO);
                });

                String username = AlteningService.login(account);
                MinecraftClient.getInstance().execute(() -> {
                    errorAlts.remove(account.getToken());
                    saveData();
                    setStatus("Logged in as " + username, STATUS_OK);
                    loading = false;
                    generateEnabled = true;
                });
            } catch (Throwable throwable) {
                MinecraftClient.getInstance().execute(() -> {
                    setStatus(formatStatusError(throwable), STATUS_ERROR);
                    loading = false;
                    generateEnabled = true;
                });
            }
        }, "Smoke-AltGenerate");
        worker.setDaemon(true);
        worker.start();
    }

    private void loginSelected() {
        if (selectedIndex < 0 || selectedIndex >= alts.size() || loading) {
            return;
        }
        loginAlt(alts.get(selectedIndex));
    }

    private void loginAlt(AltAccount alt) {
        if (loading) {
            return;
        }

        loading = true;
        setStatus("Logging in as " + alt.getUsername() + "...", STATUS_INFO);

        Thread worker = new Thread(() -> {
            try {
                String username = AlteningService.login(alt);
                MinecraftClient.getInstance().execute(() -> {
                    errorAlts.remove(alt.getToken());
                    saveData();
                    setStatus("Logged in as " + username, STATUS_OK);
                    loading = false;
                });
            } catch (Throwable throwable) {
                MinecraftClient.getInstance().execute(() -> {
                    errorAlts.add(alt.getToken());
                    setStatus(formatStatusError(throwable), STATUS_ERROR);
                    loading = false;
                });
            }
        }, "Smoke-AltLogin");
        worker.setDaemon(true);
        worker.start();
    }

    private void deleteSelected() {
        if (selectedIndex < 0 || selectedIndex >= alts.size()) {
            return;
        }
        alts.remove(selectedIndex);
        if (selectedIndex >= alts.size()) {
            selectedIndex = alts.size() - 1;
        }
        saveData();
    }

    private void restoreOriginalSession() {
        if (!AlteningService.hasOriginalSession()) {
            setStatus("No alt session active.", TEXT_DIM);
            return;
        }
        AlteningService.restoreOriginalSession();
        setStatus("Restored original session.", STATUS_OK);
        selectedIndex = -1;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        panelLeft = this.width / 2 - PANEL_WIDTH / 2;
        panelRight = panelLeft + PANEL_WIDTH;
        panelTop = PANEL_MARGIN;
        panelBottom = this.height - PANEL_MARGIN;

        context.fillGradient(0, 0, this.width, this.height, 0xF0101010, 0xF0010101);
        context.fill(panelLeft, panelTop, panelRight, panelBottom, 0xF5151515);
        border(context, panelLeft, panelTop, PANEL_WIDTH, panelBottom - panelTop, 0xFF2A2A2A);
        context.fill(panelLeft, panelTop, panelRight, panelTop + 2, ACCENT);

        context.drawText(this.textRenderer, Text.literal("Alt Manager"), panelLeft + 10, panelTop + 8, TEXT_COLOR, false);

        int apiY = panelTop + 38;
        context.drawText(this.textRenderer, Text.literal("API Key"), panelLeft + 12, apiY + 5, TEXT_COLOR, false);

        setRect(generateButton, panelRight - 80, apiY - 1, 68, BUTTON_HEIGHT);
        drawButton(context, generateButton, mouseX, mouseY, "Generate", generateEnabled ? 0xFF2B6CB0 : 0xFF333333, true);

        int actionsY = apiY + 26;
        int gap = 8;
        int totalWidth = PANEL_WIDTH - 24;
        int buttonWidth = (totalWidth - gap * 2) / 3;
        setRect(loginButton, panelLeft + 12, actionsY, buttonWidth, BUTTON_HEIGHT);
        setRect(restoreButton, panelLeft + 12 + buttonWidth + gap, actionsY, buttonWidth, BUTTON_HEIGHT);
        setRect(deleteButton, panelLeft + 12 + (buttonWidth + gap) * 2, actionsY, buttonWidth, BUTTON_HEIGHT);

        drawButton(context, loginButton, mouseX, mouseY, "Login", 0xFF2D8CFF, true);
        drawButton(context, restoreButton, mouseX, mouseY, "Restore", 0xFF4A4A4A, true);
        drawButton(context, deleteButton, mouseX, mouseY, "Delete", 0xFF9A3131, true);

        int sectionY = actionsY + BUTTON_HEIGHT + 10;
        context.fill(panelLeft + 8, sectionY, panelRight - 8, sectionY + 18, 0xFF101010);
        context.drawText(this.textRenderer, Text.literal("Accounts"), panelLeft + 14, sectionY + 5, TEXT_COLOR, false);
        String countText = alts.size() + " total";
        int countWidth = this.textRenderer.getWidth(countText);
        context.drawText(this.textRenderer, Text.literal(countText), panelRight - countWidth - 14, sectionY + 5, TEXT_DIM, false);

        listTop = sectionY + 18;
        listBottom = panelBottom - 50;
        int listHeight = listBottom - listTop;
        context.fill(panelLeft + 8, listTop, panelRight - 8, listBottom, 0xFF0A0A0A);

        int totalContentHeight = alts.size() * ENTRY_HEIGHT;
        maxScroll = Math.max(0, totalContentHeight - listHeight);
        scroll = clamp(scroll, 0, maxScroll);

        boolean altSessionActive = AlteningService.isAltSessionActive();
        String currentUsername = this.client != null ? this.client.getSession().getUsername() : "";

        if (alts.isEmpty()) {
            String empty = "No alts yet. Click Generate.";
            int width = this.textRenderer.getWidth(empty);
            context.drawText(this.textRenderer, Text.literal(empty), this.width / 2 - width / 2, listTop + listHeight / 2 - 4, TEXT_DIM, false);
        } else {
            context.enableScissor(panelLeft + 9, listTop, panelRight - 9, listBottom);
            for (int i = 0; i < alts.size(); i++) {
                int entryY = listTop + i * ENTRY_HEIGHT - scroll;
                if (entryY + ENTRY_HEIGHT < listTop || entryY > listBottom) {
                    continue;
                }

                AltAccount alt = alts.get(i);
                String username = alt.getUsername() == null || alt.getUsername().isBlank() ? "Unknown" : alt.getUsername();
                boolean selected = i == selectedIndex;
                boolean hovered = mouseX >= panelLeft + 10
                        && mouseX < panelRight - 10
                        && mouseY >= Math.max(listTop, entryY)
                        && mouseY < Math.min(listBottom, entryY + ENTRY_HEIGHT);
                boolean active = altSessionActive && username.equals(currentUsername);
                boolean failed = errorAlts.contains(alt.getToken());

                int bg = selected ? 0xFF1F1F1F : hovered ? 0xFF181818 : ((i & 1) == 0 ? 0xFF131313 : 0xFF101010);
                context.fill(panelLeft + 10, entryY, panelRight - 10, entryY + ENTRY_HEIGHT - 1, bg);

                if (selected || active) {
                    context.fill(panelLeft + 10, entryY, panelLeft + 13, entryY + ENTRY_HEIGHT - 1, active ? STATUS_OK : ACCENT);
                }

                context.drawText(this.textRenderer, Text.literal(username), panelLeft + 18, entryY + 8, active ? STATUS_OK : TEXT_COLOR, false);
                context.drawText(this.textRenderer, Text.literal(maskToken(alt.getToken())), panelLeft + 170, entryY + 8, TEXT_DIM, false);

                String state = active ? "ONLINE" : (failed ? "ERROR" : "OFFLINE");
                int stateColor = active ? STATUS_OK : (failed ? STATUS_ERROR : TEXT_DIM);
                int stateWidth = this.textRenderer.getWidth(state);
                context.drawText(this.textRenderer, Text.literal(state), panelRight - stateWidth - 18, entryY + 8, stateColor, false);
            }
            context.disableScissor();

            if (maxScroll > 0) {
                int scrollbarX = panelRight - 12;
                float ratio = (float) listHeight / Math.max(1, totalContentHeight);
                int thumbHeight = Math.max(20, (int) (listHeight * ratio));
                int thumbY = listTop + (int) ((listHeight - thumbHeight) * (scroll / (float) maxScroll));
                context.fill(scrollbarX, listTop, scrollbarX + 3, listBottom, 0xFF222222);
                context.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbHeight, 0xFF707070);
            }
        }

        int statusY = listBottom + 6;
        if (!status.isEmpty()) {
            int alpha = 255;
            long age = System.currentTimeMillis() - statusTime;
            if (age > 7000L) {
                alpha = Math.max(0, 255 - (int) ((age - 7000L) / 6L));
            }
            if (alpha > 0) {
                int faded = (statusColor & 0x00FFFFFF) | (alpha << 24);
                context.drawText(this.textRenderer, Text.literal(status), panelLeft + 10, statusY, faded, false);
            }
        }

        String sessionLine = (AlteningService.isAltSessionActive() ? "Alt: " : "Original: ")
                + (this.client != null ? this.client.getSession().getUsername() : "Unknown");
        context.drawText(this.textRenderer, Text.literal(sessionLine), panelLeft + 10, statusY + 12, TEXT_DIM, false);

        int bottomY = panelBottom - 26;
        setRect(backButton, panelRight - 72, bottomY, 60, BUTTON_HEIGHT);
        drawButton(context, backButton, mouseX, mouseY, "Back", 0xFF3F3F3F, true);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (button == 0) {
            if (inRect(mx, my, generateButton) && generateEnabled) {
                generateAlt();
                return true;
            }
            if (inRect(mx, my, loginButton)) {
                loginSelected();
                return true;
            }
            if (inRect(mx, my, restoreButton)) {
                restoreOriginalSession();
                return true;
            }
            if (inRect(mx, my, deleteButton)) {
                deleteSelected();
                return true;
            }
            if (inRect(mx, my, backButton)) {
                close();
                return true;
            }

            if (mx >= panelLeft + 10 && mx < panelRight - 10 && my >= listTop && my < listBottom) {
                for (int i = 0; i < alts.size(); i++) {
                    int entryY = listTop + i * ENTRY_HEIGHT - scroll;
                    if (entryY + ENTRY_HEIGHT < listTop || entryY > listBottom) {
                        continue;
                    }
                    if (my < Math.max(listTop, entryY) || my >= Math.min(listBottom, entryY + ENTRY_HEIGHT)) {
                        continue;
                    }

                    long now = System.currentTimeMillis();
                    if (i == lastClickedIndex && (now - lastClickTime) < DOUBLE_CLICK_MS && !loading) {
                        loginAlt(alts.get(i));
                        lastClickedIndex = -1;
                        return true;
                    }

                    selectedIndex = i;
                    lastClickedIndex = i;
                    lastClickTime = now;
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= panelLeft + 8 && mouseX < panelRight - 8 && mouseY >= listTop && mouseY < listBottom) {
            scroll -= (int) (verticalAmount * ENTRY_HEIGHT * 1.5D);
            scroll = clamp(scroll, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            if (selectedIndex >= 0 && selectedIndex < alts.size() && !loading) {
                loginAlt(alts.get(selectedIndex));
                return true;
            }
        }
        if (keyCode == 261) {
            deleteSelected();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void saveData() {
        AlteningService.AltData data = new AlteningService.AltData();
        apiKey = apiKeyField != null ? apiKeyField.getText().trim() : apiKey;
        data.apiKey = apiKey;
        data.alts = new ArrayList<>(alts);
        AlteningService.saveData(data);
    }

    private void setStatus(String message, int color) {
        status = message == null ? "" : message;
        statusColor = color;
        statusTime = System.currentTimeMillis();
    }

    private String formatStatusError(Throwable throwable) {
        if (throwable == null) {
            return "Operation failed.";
        }
        if (AlteningApiClient.isPkixPathFailure(throwable)) {
            return "Certificate trust failed (PKIX). Check clock and HTTPS scanning.";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName() + " during request.";
        }
        return message;
    }

    private static String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "(hidden)";
        }
        String cleaned = token.trim();
        int tailSize = Math.min(4, cleaned.length());
        String tail = cleaned.substring(cleaned.length() - tailSize);
        int maskSize = Math.max(6, Math.min(14, cleaned.length() - tailSize));
        return "token " + "*".repeat(maskSize) + tail;
    }

    private void drawButton(DrawContext context, int[] rect, int mouseX, int mouseY, String label, int color, boolean enabled) {
        boolean hovered = enabled && inRect(mouseX, mouseY, rect);
        int bg = enabled ? (hovered ? lighten(color, 0.18F) : color) : 0xFF2D2D2D;
        context.fill(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3], bg);
        border(context, rect[0], rect[1], rect[2], rect[3], 0xFF111111);

        int textWidth = this.textRenderer.getWidth(label);
        int textX = rect[0] + rect[2] / 2 - textWidth / 2;
        int textY = rect[1] + 6;
        int textColor = enabled ? TEXT_COLOR : TEXT_DIM;
        context.drawText(this.textRenderer, Text.literal(label), textX, textY, textColor, false);
    }

    private static int lighten(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.min(255, (int) (r + (255 - r) * factor));
        g = Math.min(255, (int) (g + (255 - g) * factor));
        b = Math.min(255, (int) (b + (255 - b) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void border(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void setRect(int[] rect, int x, int y, int w, int h) {
        rect[0] = x;
        rect[1] = y;
        rect[2] = w;
        rect[3] = h;
    }

    private static boolean inRect(int x, int y, int[] rect) {
        return x >= rect[0] && x < rect[0] + rect[2] && y >= rect[1] && y < rect[1] + rect[3];
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
