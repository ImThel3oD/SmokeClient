package com.smoke.client.module;

public interface ClientSessionListener {
    default void onClientDisconnect() {
    }

    default void onWorldChange() {
    }
}
