package com.smoke.client.event.events;

import com.smoke.client.event.Event;

public final class HandleInputEvent implements Event {
    public enum Phase {
        PRE,
        POST
    }

    private final Phase phase;

    private HandleInputEvent(Phase phase) {
        this.phase = phase;
    }

    public static HandleInputEvent pre() {
        return new HandleInputEvent(Phase.PRE);
    }

    public static HandleInputEvent post() {
        return new HandleInputEvent(Phase.POST);
    }

    public Phase phase() {
        return phase;
    }
}

