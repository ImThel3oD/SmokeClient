package com.smoke.client.event;

public abstract class CancellableEvent implements Event {
    private boolean cancelled;
    private boolean propagationStopped;

    public final boolean isCancelled() {
        return cancelled;
    }

    public final void cancel() {
        this.cancelled = true;
    }

    public final boolean isPropagationStopped() {
        return propagationStopped;
    }

    public final void stopPropagation() {
        this.propagationStopped = true;
    }
}
