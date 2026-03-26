package com.smoke.client.network;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.Nullable;

public interface ImmediatePacketSender {
    void smoke$sendImmediately(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush);
}
