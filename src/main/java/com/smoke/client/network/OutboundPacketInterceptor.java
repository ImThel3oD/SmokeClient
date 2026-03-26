package com.smoke.client.network;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.Nullable;

public interface OutboundPacketInterceptor {
    boolean interceptOutbound(ClientConnection connection, Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush);
}
