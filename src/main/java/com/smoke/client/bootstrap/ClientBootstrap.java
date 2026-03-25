package com.smoke.client.bootstrap;

public final class ClientBootstrap {
    private ClientBootstrap() {
    }

    public static ClientRuntime start() {
        ClientRuntime runtime = new ClientRuntime();
        runtime.start();
        return runtime;
    }
}
