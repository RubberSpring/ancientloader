package net.ancientloader.mixin;

public class CallbackInfo {

    private boolean cancelled;

    public CallbackInfo() {
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
