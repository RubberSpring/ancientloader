package net.ancientloader.core.mixin;

import com.mojang.rubydung.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Player.class)
public interface PlayerInvoker {
    @Invoker("resetPos")
    void invokeResetPos();
}
