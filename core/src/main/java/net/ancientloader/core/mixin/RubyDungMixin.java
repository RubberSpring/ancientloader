package net.ancientloader.core.mixin;

import com.mojang.rubydung.RubyDung;
import com.mojang.rubydung.level.Tile;
import net.ancientloader.core.AncientCore;
import net.ancientloader.core.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(RubyDung.class)
public class RubyDungMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void registerBlocks(CallbackInfo ci) {
        List<Integer> nonFilledLayers = getNonFilledLayers();

        AncientCore.blocks.put("GRASS", new Block(Tile.grass, new int[]{43}));
        if (nonFilledLayers.contains(43)) {
            AncientCore.blocks.put("GRASS", new Block(Tile.grass, new int[]{43}));
            nonFilledLayers.remove(43);
        }

        if (!nonFilledLayers.isEmpty()) {
            AncientCore.blocks.put("ROCK",
                    new Block(Tile.rock, nonFilledLayers.stream().mapToInt(Integer::valueOf).toArray())
            );
        }
    }

    @Unique
    private static List<Integer> getNonFilledLayers() {
        Set<Integer> filledLayers = new HashSet<>();

        for (Block block : AncientCore.blocks.values()) {
            for (int layer : block.layer) {
                if (layer >= 0 && layer <= 64) {
                    filledLayers.add(layer);
                }
            }
        }

        List<Integer> nonFilledLayers = new ArrayList<>();

        for (int layer = 0; layer <= 64; layer++) {
            if (!filledLayers.contains(layer)) {
                nonFilledLayers.add(layer);
            }
        }
        return nonFilledLayers;
    }
}
