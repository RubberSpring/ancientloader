package net.ancientloader.examplemod.mixin;

import com.mojang.rubydung.RubyDung;
import net.ancientloader.mixin.At;
import net.ancientloader.mixin.CallbackInfo;
import net.ancientloader.mixin.Inject;
import net.ancientloader.mixin.Mixin;

@Mixin(RubyDung.class)
public class RubyDungMixin {
    @Inject(
            method = "init()v",
            at = @At("HEAD"))
    private void injected(CallbackInfo ci) {
        System.out.println("hello from mixin");
    }
}
