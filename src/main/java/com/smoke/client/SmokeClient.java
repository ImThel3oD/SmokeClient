package com.smoke.client;

import com.smoke.client.bootstrap.ClientBootstrap;
import com.smoke.client.bootstrap.ClientRuntime;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SmokeClient implements ClientModInitializer {
    public static final String MOD_ID = "smoke";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ClientRuntime runtime;

    public static ClientRuntime getRuntime() {
        return runtime;
    }

    @Override
    public void onInitializeClient() {
        runtime = ClientBootstrap.start();
        LOGGER.info("Smoke runtime initialized");
    }
}
