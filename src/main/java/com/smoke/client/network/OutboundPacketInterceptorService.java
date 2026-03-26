package com.smoke.client.network;

import com.smoke.client.module.Module;
import com.smoke.client.module.ModuleManager;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class OutboundPacketInterceptorService {
    private final ModuleManager moduleManager;

    public OutboundPacketInterceptorService(ModuleManager moduleManager) {
        this.moduleManager = Objects.requireNonNull(moduleManager, "moduleManager");
    }

    public boolean intercept(ClientConnection connection, Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
        for (Module module : moduleManager.enabledModules()) {
            if (module instanceof OutboundPacketInterceptor interceptor
                    && interceptor.interceptOutbound(connection, packet, listener, flush)) {
                return true;
            }
        }
        return false;
    }
}
