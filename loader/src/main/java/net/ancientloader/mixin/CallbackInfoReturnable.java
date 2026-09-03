package net.ancientloader.mixin;

public class CallbackInfoReturnable<T> extends CallbackInfo {

    private T returnValue;

    public CallbackInfoReturnable() {
    }

    public CallbackInfoReturnable(T returnValue) {
        this.returnValue = returnValue;
    }

    public T getReturnValue() {
        return returnValue;
    }

    public void setReturnValue(T returnValue) {
        this.returnValue = returnValue;
        cancel();
    }
}
