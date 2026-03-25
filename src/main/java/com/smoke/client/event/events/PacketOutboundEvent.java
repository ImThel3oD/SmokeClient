package com.smoke.client.event.events;

import com.smoke.client.event.CancellableEvent;
import net.minecraft.network.packet.Packet;

import java.util.Objects;

public final class PacketOutboundEvent extends CancellableEvent {
    private Packet<?> packet;

    public PacketOutboundEvent(Packet<?> packet) {
        this.packet = Objects.requireNonNull(packet, "packet");
    }

    public Packet<?> packet() {
        return packet;
    }

    public void replace(Packet<?> replacement) {
        this.packet = Objects.requireNonNull(replacement, "replacement");
    }
}
