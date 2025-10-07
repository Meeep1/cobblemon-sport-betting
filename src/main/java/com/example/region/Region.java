package com.example.region;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class Region {
    public Identifier dimension; // e.g., minecraft:overworld
    public BlockPos min;
    public BlockPos max;

    public Region(Identifier dimension, BlockPos a, BlockPos b) {
        this.dimension = dimension;
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        this.min = new BlockPos(minX, minY, minZ);
        this.max = new BlockPos(maxX, maxY, maxZ);
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }
}
