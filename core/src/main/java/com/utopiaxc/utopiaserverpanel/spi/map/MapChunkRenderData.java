package com.utopiaxc.utopiaserverpanel.spi.map;

/**
 * Top-down 16x16 chunk pixel capture (base RGB, top Y height, biome/water flags)
 * captured safely on the server thread for JourneyMap-style shading and tile caching.
 */
public final class MapChunkRenderData {
    private final String dimension;
    private final int chunkX;
    private final int chunkZ;
    private final int[] argbPixels; // length 256 (z * 16 + x)
    private final short[] heights;  // length 256 (z * 16 + x)
    private final boolean hasCeiling;

    public MapChunkRenderData(String dimension, int chunkX, int chunkZ, int[] argbPixels, short[] heights, boolean hasCeiling) {
        this.dimension = dimension;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.argbPixels = argbPixels;
        this.heights = heights;
        this.hasCeiling = hasCeiling;
    }

    public String getDimension() { return dimension; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public int[] getArgbPixels() { return argbPixels; }
    public short[] getHeights() { return heights; }
    public boolean isHasCeiling() { return hasCeiling; }
}
