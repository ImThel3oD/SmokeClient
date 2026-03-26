package com.smoke.client.network;

import com.smoke.client.event.EventBus;
import com.smoke.client.event.events.PacketInboundEvent;
import com.smoke.client.event.events.PacketOutboundEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;

import java.util.Objects;

public final class PacketService {
    private final EventBus eventBus;
    private final ThreadLocal<PreparedDecision> preparedOutbound = new ThreadLocal<>();
    private final ThreadLocal<PreparedDecision> preparedInbound = new ThreadLocal<>();

    public PacketService(EventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    public PacketDecision prepareOutbound(Packet<?> packet) {
        return prepare(packet, preparedOutbound, true);
    }

    public PacketDecision prepareInbound(Packet<?> packet) {
        return prepare(packet, preparedInbound, false);
    }

    public Packet<?> rewriteOutbound(Packet<?> packet) {
        return prepareOutbound(packet).packet();
    }

    public Packet<?> rewriteInbound(Packet<?> packet) {
        return prepareInbound(packet).packet();
    }

    public void clearPreparedOutbound(Packet<?> packet) {
        clear(packet, preparedOutbound);
    }

    public void clearPreparedInbound(Packet<?> packet) {
        clear(packet, preparedInbound);
    }

    public void clearPreparedOutbound() {
        preparedOutbound.remove();
    }

    public void clearPreparedInbound() {
        preparedInbound.remove();
    }

    public PacketDecision handleOutbound(Packet<?> packet) {
        PacketOutboundEvent event = new PacketOutboundEvent(packet);
        eventBus.post(event);
        return new PacketDecision(event.packet(), event.isCancelled());
    }

    public PacketDecision handleInbound(Packet<?> packet) {
        PacketInboundEvent event = new PacketInboundEvent(packet);
        eventBus.post(event);
        return new PacketDecision(event.packet(), event.isCancelled());
    }

    public boolean send(Packet<?> packet) {
        Objects.requireNonNull(packet, "packet");
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
            return false;
        }

        client.getNetworkHandler().sendPacket(packet);
        return true;
    }

    private PacketDecision prepare(Packet<?> packet, ThreadLocal<PreparedDecision> storage, boolean outbound) {
        PreparedDecision prepared = storage.get();
        if (prepared != null && prepared.original() == packet) {
            return prepared.decision();
        }

        PacketDecision decision = outbound ? handleOutbound(packet) : handleInbound(packet);
        storage.set(new PreparedDecision(packet, decision));
        return decision;
    }

    private static void clear(Packet<?> packet, ThreadLocal<PreparedDecision> storage) {
        PreparedDecision prepared = storage.get();
        if (prepared != null && prepared.original() == packet) {
            storage.remove();
        }
    }

    public record PacketDecision(Packet<?> packet, boolean cancelled) {
    }

    private record PreparedDecision(Packet<?> original, PacketDecision decision) {
    }
}
