package com.smoke.client.alt;

record AltScreenLayout(
        AltRect panel,
        int apiY,
        AltRect apiKeyField,
        AltRect generateButton,
        AltRect loginButton,
        AltRect restoreButton,
        AltRect deleteButton,
        AltRect accountsHeader,
        AltRect list,
        AltRect backButton
) {
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_MARGIN = 22;
    private static final int BUTTON_HEIGHT = 20;

    static AltScreenLayout create(int screenWidth, int screenHeight) {
        int panelLeft = screenWidth / 2 - PANEL_WIDTH / 2;
        int panelTop = PANEL_MARGIN;
        int panelBottom = screenHeight - PANEL_MARGIN;
        AltRect panel = new AltRect(panelLeft, panelTop, PANEL_WIDTH, panelBottom - panelTop);
        int panelRight = panel.right();

        int apiY = panelTop + 38;
        AltRect apiKeyField = new AltRect(panelLeft + 95, apiY, PANEL_WIDTH - 180, 18);
        AltRect generateButton = new AltRect(panelRight - 80, apiY - 1, 68, BUTTON_HEIGHT);

        int actionsY = apiY + 26;
        int gap = 8;
        int totalWidth = PANEL_WIDTH - 24;
        int buttonWidth = (totalWidth - gap * 2) / 3;
        AltRect loginButton = new AltRect(panelLeft + 12, actionsY, buttonWidth, BUTTON_HEIGHT);
        AltRect restoreButton = new AltRect(panelLeft + 12 + buttonWidth + gap, actionsY, buttonWidth, BUTTON_HEIGHT);
        AltRect deleteButton = new AltRect(panelLeft + 12 + ((buttonWidth + gap) * 2), actionsY, buttonWidth, BUTTON_HEIGHT);

        int sectionY = actionsY + BUTTON_HEIGHT + 10;
        AltRect accountsHeader = new AltRect(panelLeft + 8, sectionY, PANEL_WIDTH - 16, 18);

        int listTop = accountsHeader.bottom();
        int listBottom = panelBottom - 50;
        AltRect list = new AltRect(panelLeft + 8, listTop, PANEL_WIDTH - 16, listBottom - listTop);

        AltRect backButton = new AltRect(panelRight - 72, panelBottom - 26, 60, BUTTON_HEIGHT);
        return new AltScreenLayout(panel, apiY, apiKeyField, generateButton, loginButton, restoreButton, deleteButton, accountsHeader, list, backButton);
    }
}
