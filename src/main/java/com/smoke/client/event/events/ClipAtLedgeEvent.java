package com.smoke.client.event.events;

import com.smoke.client.event.Event;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.Objects;

public final class ClipAtLedgeEvent implements Event {
    private final ClientPlayerEntity player;
    private boolean forceClip;

    public ClipAtLedgeEvent(ClientPlayerEntity player) {
        this.player = Objects.requireNonNull(player, "player");
    }

    public ClientPlayerEntity player() {
        return player;
    }

    public boolean forceClip() {
        return forceClip;
    }

    public void forceClip(boolean forceClip) {
        this.forceClip = forceClip;
    }
}
