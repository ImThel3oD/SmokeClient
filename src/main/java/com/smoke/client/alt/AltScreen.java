package com.smoke.client.alt;

import com.smoke.client.util.PrivacySanitizer;
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
    private static final int ENTRY_HEIGHT = 30;
    private static final long DOUBLE_CLICK_MS = 350L;
    private static final int ACCENT = 0xFF4DA6FF;
    private static final int STATUS_ERROR = 0xFFFF6666;
    private static final int STATUS_INFO = 0xFFFFD166;
    private static final int STATUS_OK = 0xFF7CFF9A;
    private static final int TEXT_COLOR = 0xFFE6E6E6;
    private static final int TEXT_DIM = 0xFF9A9A9A;

    private final Screen parent;
    private TextFieldWidget apiKeyField;
    private AltScreenLayout layout;

    private String apiKey = "";
    private List<AltAccount> alts = new ArrayList<>();
    private final Set<String> errorAlts = ConcurrentHashMap.newKeySet();

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
        refreshLayout();

        apiKeyField = new TextFieldWidget(
                this.textRenderer,
                layout.apiKeyField().x(),
                layout.apiKeyField().y(),
                layout.apiKeyField().width(),
                layout.apiKeyField().height(),
                Text.literal("API Key")
        );
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

        startWorker("Smoke-AltGenerate", () -> {
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
        });
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

        startWorker("Smoke-AltLogin", () -> {
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
        });
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
        refreshLayout();
        AltRect panel = layout.panel();
        AltRect listRect = layout.list();
        if (apiKeyField != null) {
            apiKeyField.setX(layout.apiKeyField().x());
            apiKeyField.setY(layout.apiKeyField().y());
            apiKeyField.setWidth(layout.apiKeyField().width());
        }

        context.fillGradient(0, 0, this.width, this.height, 0xF0101010, 0xF0010101);
        context.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), 0xF5151515);
        AltScreenPaint.drawBorder(context, panel, 0xFF2A2A2A);
        context.fill(panel.x(), panel.y(), panel.right(), panel.y() + 2, ACCENT);

        context.drawText(this.textRenderer, Text.literal("Alt Manager"), panel.x() + 10, panel.y() + 8, TEXT_COLOR, false);

        context.drawText(this.textRenderer, Text.literal("API Key"), panel.x() + 12, layout.apiY() + 5, TEXT_COLOR, false);

        AltScreenPaint.drawButton(
                context,
                this.textRenderer,
                layout.generateButton(),
                mouseX,
                mouseY,
                "Generate",
                generateEnabled ? 0xFF2B6CB0 : 0xFF333333,
                true,
                TEXT_COLOR,
                TEXT_DIM
        );

        AltScreenPaint.drawButton(context, this.textRenderer, layout.loginButton(), mouseX, mouseY, "Login", 0xFF2D8CFF, true, TEXT_COLOR, TEXT_DIM);
        AltScreenPaint.drawButton(context, this.textRenderer, layout.restoreButton(), mouseX, mouseY, "Restore", 0xFF4A4A4A, true, TEXT_COLOR, TEXT_DIM);
        AltScreenPaint.drawButton(context, this.textRenderer, layout.deleteButton(), mouseX, mouseY, "Delete", 0xFF9A3131, true, TEXT_COLOR, TEXT_DIM);

        context.fill(layout.accountsHeader().x(), layout.accountsHeader().y(), layout.accountsHeader().right(), layout.accountsHeader().bottom(), 0xFF101010);
        context.drawText(this.textRenderer, Text.literal("Accounts"), panel.x() + 14, layout.accountsHeader().y() + 5, TEXT_COLOR, false);
        String countText = alts.size() + " total";
        int countWidth = this.textRenderer.getWidth(countText);
        context.drawText(this.textRenderer, Text.literal(countText), panel.right() - countWidth - 14, layout.accountsHeader().y() + 5, TEXT_DIM, false);

        int listHeight = listRect.height();
        context.fill(listRect.x(), listRect.y(), listRect.right(), listRect.bottom(), 0xFF0A0A0A);

        int totalContentHeight = alts.size() * ENTRY_HEIGHT;
        maxScroll = Math.max(0, totalContentHeight - listHeight);
        scroll = clamp(scroll, 0, maxScroll);

        boolean altSessionActive = AlteningService.isAltSessionActive();
        String currentUsername = this.client != null ? this.client.getSession().getUsername() : "";

        if (alts.isEmpty()) {
            String empty = "No alts yet. Click Generate.";
            int width = this.textRenderer.getWidth(empty);
            context.drawText(this.textRenderer, Text.literal(empty), this.width / 2 - width / 2, listRect.y() + listHeight / 2 - 4, TEXT_DIM, false);
        } else {
            context.enableScissor(listRect.x() + 1, listRect.y(), listRect.right() - 1, listRect.bottom());
            for (int i = 0; i < alts.size(); i++) {
                int entryY = listRect.y() + i * ENTRY_HEIGHT - scroll;
                if (entryY + ENTRY_HEIGHT < listRect.y() || entryY > listRect.bottom()) {
                    continue;
                }

                AltAccount alt = alts.get(i);
                String username = alt.getUsername() == null || alt.getUsername().isBlank() ? "Unknown" : alt.getUsername();
                boolean selected = i == selectedIndex;
                boolean hovered = mouseX >= listRect.x() + 2
                        && mouseX < listRect.right() - 2
                        && mouseY >= Math.max(listRect.y(), entryY)
                        && mouseY < Math.min(listRect.bottom(), entryY + ENTRY_HEIGHT);
                boolean active = altSessionActive && username.equals(currentUsername);
                boolean failed = errorAlts.contains(alt.getToken());

                int bg = selected ? 0xFF1F1F1F : hovered ? 0xFF181818 : ((i & 1) == 0 ? 0xFF131313 : 0xFF101010);
                context.fill(listRect.x() + 2, entryY, listRect.right() - 2, entryY + ENTRY_HEIGHT - 1, bg);

                if (selected || active) {
                    context.fill(listRect.x() + 2, entryY, listRect.x() + 5, entryY + ENTRY_HEIGHT - 1, active ? STATUS_OK : ACCENT);
                }

                context.drawText(this.textRenderer, Text.literal(username), listRect.x() + 10, entryY + 8, active ? STATUS_OK : TEXT_COLOR, false);
                context.drawText(this.textRenderer, Text.literal(maskToken(alt.getToken())), listRect.x() + 162, entryY + 8, TEXT_DIM, false);

                String state = active ? "ONLINE" : (failed ? "ERROR" : "OFFLINE");
                int stateColor = active ? STATUS_OK : (failed ? STATUS_ERROR : TEXT_DIM);
                int stateWidth = this.textRenderer.getWidth(state);
                context.drawText(this.textRenderer, Text.literal(state), panel.right() - stateWidth - 18, entryY + 8, stateColor, false);
            }
            context.disableScissor();

            if (maxScroll > 0) {
                int scrollbarX = listRect.right() - 4;
                float ratio = (float) listHeight / Math.max(1, totalContentHeight);
                int thumbHeight = Math.max(20, (int) (listHeight * ratio));
                int thumbY = listRect.y() + (int) ((listHeight - thumbHeight) * (scroll / (float) maxScroll));
                context.fill(scrollbarX, listRect.y(), scrollbarX + 3, listRect.bottom(), 0xFF222222);
                context.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbHeight, 0xFF707070);
            }
        }

        int statusY = listRect.bottom() + 6;
        if (!status.isEmpty()) {
            int alpha = 255;
            long age = System.currentTimeMillis() - statusTime;
            if (age > 7000L) {
                alpha = Math.max(0, 255 - (int) ((age - 7000L) / 6L));
            }
            if (alpha > 0) {
                int faded = (statusColor & 0x00FFFFFF) | (alpha << 24);
                context.drawText(this.textRenderer, Text.literal(status), panel.x() + 10, statusY, faded, false);
            }
        }

        String sessionLine = (AlteningService.isAltSessionActive() ? "Alt: " : "Original: ")
                + (this.client != null ? this.client.getSession().getUsername() : "Unknown");
        context.drawText(this.textRenderer, Text.literal(sessionLine), panel.x() + 10, statusY + 12, TEXT_DIM, false);

        AltScreenPaint.drawButton(context, this.textRenderer, layout.backButton(), mouseX, mouseY, "Back", 0xFF3F3F3F, true, TEXT_COLOR, TEXT_DIM);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        AltScreenLayout currentLayout = layout();
        AltRect listRect = currentLayout.list();

        if (button == 0) {
            if (currentLayout.generateButton().contains(mx, my) && generateEnabled) {
                generateAlt();
                return true;
            }
            if (currentLayout.loginButton().contains(mx, my)) {
                loginSelected();
                return true;
            }
            if (currentLayout.restoreButton().contains(mx, my)) {
                restoreOriginalSession();
                return true;
            }
            if (currentLayout.deleteButton().contains(mx, my)) {
                deleteSelected();
                return true;
            }
            if (currentLayout.backButton().contains(mx, my)) {
                close();
                return true;
            }

            if (mx >= listRect.x() + 2 && mx < listRect.right() - 2 && my >= listRect.y() && my < listRect.bottom()) {
                for (int i = 0; i < alts.size(); i++) {
                    int entryY = listRect.y() + i * ENTRY_HEIGHT - scroll;
                    if (entryY + ENTRY_HEIGHT < listRect.y() || entryY > listRect.bottom()) {
                        continue;
                    }
                    if (my < Math.max(listRect.y(), entryY) || my >= Math.min(listRect.bottom(), entryY + ENTRY_HEIGHT)) {
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
        if (layout().list().contains(mouseX, mouseY)) {
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
        return PrivacySanitizer.sanitize(message.trim());
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

    private AltScreenLayout layout() {
        if (layout == null) {
            refreshLayout();
        }
        return layout;
    }

    private void refreshLayout() {
        layout = AltScreenLayout.create(this.width, this.height);
    }

    private static void startWorker(String threadName, Runnable task) {
        Thread worker = new Thread(task, threadName);
        worker.setDaemon(true);
        worker.start();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
