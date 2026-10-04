package net.ancientloader.core.mixin;

import com.mojang.rubydung.level.Chunk;
import com.mojang.rubydung.level.Level;
import com.mojang.rubydung.level.Tesselator;
import com.mojang.rubydung.level.Tile;
import net.ancientloader.core.AncientCore;
import net.ancientloader.core.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Chunk.class)
public class ChunkMixin {
    @Unique
    private static Tile layerToTile(int y) {
        for (Block block : AncientCore.blocks.values()) {
            for (int layer : block.layer) {
                if (layer == y) {
                    return block.tile;
                }
            }
        }
        return Tile.rock;
    }

    @Redirect(
            method = "rebuild",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/rubydung/level/Tile;render(Lcom/mojang/rubydung/level/Tesselator;Lcom/mojang/rubydung/level/Level;IIII)V"
            )
    )
    private void replaceRender(
            Tile tile,
            Tesselator t,
            Level level,
            int layer,
            int x,
            int y,
            int z
    ) {
        Tile customTile = layerToTile(y);
        customTile.render(t, level, layer, x, y, z);
    }
}
