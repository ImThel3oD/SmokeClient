package com.smoke.client.event.events;

import com.smoke.client.event.Event;

public final class TickEvent implements Event {
    public enum Phase {
        PRE,
        POST
    }

    private final Phase phase;

    private TickEvent(Phase phase) {
        this.phase = phase;
    }

    public static TickEvent pre() {
        return new TickEvent(Phase.PRE);
    }

    public static TickEvent post() {
        return new TickEvent(Phase.POST);
    }

    public Phase phase() {
        return phase;
    }
}
