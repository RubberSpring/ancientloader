package net.ancientloader.core.mixin;

import com.mojang.rubydung.Player;
import com.mojang.rubydung.RubyDung;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RubyDung.class)
public interface PlayerAccessor {
    @Accessor("player")
    Player getPlayer();
}
