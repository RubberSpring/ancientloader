package net.ancientloader.examplemod.mixin;

import com.mojang.rubydung.RubyDung;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RubyDung.class)
public class RubyDungMixin {
    @Inject(
            method = "init()V",
            at = @At("HEAD"),
            remap = false)
    private void ancientloader$injected(CallbackInfo ci) {
        System.out.println("hello from mixin");
    }
}
