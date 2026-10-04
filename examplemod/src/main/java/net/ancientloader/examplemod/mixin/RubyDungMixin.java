package net.ancientloader.examplemod.mixin;

import com.mojang.rubydung.RubyDung;
import com.mojang.rubydung.level.Tile;
import net.ancientloader.core.AncientCore;
import net.ancientloader.core.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

@Mixin(RubyDung.class)
public class RubyDungMixin {
    @Inject(method = "init", at = @At("HEAD"))
    private void registerBlocks(CallbackInfo ci) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Constructor<Tile> constructor =
                Tile.class.getDeclaredConstructor(int.class);

        constructor.setAccessible(true);

        Tile customTile = constructor.newInstance(3);

        AncientCore.blocks.put("MYBLOCK", new Block(customTile, new int[]{44}));
    }
}
