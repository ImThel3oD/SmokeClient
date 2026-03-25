package com.smoke.client.alt;


public final class AltAccount {
    private String token;
    private String username;


    private transient String accessToken;
    private transient String uuid;

    public AltAccount(String token, String username) {
        this.token = token;
        this.username = username;
    }

    public String getToken() {
        return token;
    }

    public String getUsername() {
        return username;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getUuid() {
        return uuid;
    }

    public void setSession(String accessToken, String uuid, String resolvedUsername) {
        this.accessToken = accessToken;
        this.uuid = uuid;
        if (resolvedUsername != null && !resolvedUsername.isBlank()) {
            this.username = resolvedUsername;
        }
    }
}
