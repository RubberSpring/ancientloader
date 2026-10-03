package net.ancientloader.core;

import com.mojang.rubydung.level.Tile;

public class Block {
    public Tile tile;
    public int[] layer;

    public Block(Tile inputTile, int[] inputLayer) {
        tile = inputTile;
        layer = inputLayer;
    }
}
