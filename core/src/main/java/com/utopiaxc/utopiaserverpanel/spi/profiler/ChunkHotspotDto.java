package com.utopiaxc.utopiaserverpanel.spi.profiler;

/**
 * High-load chunk hotspot entry for entity or block-entity density ranking.
 */
public final class ChunkHotspotDto {
    private final String dimension;
    private final int chunkX;
    private final int chunkZ;
    private final int centerBlockX;
    private final int centerBlockZ;
    private final int entityCount;
    private final int blockEntityCount;
    private final String topSummary;

    public ChunkHotspotDto(String dimension, int chunkX, int chunkZ, int entityCount, int blockEntityCount, String topSummary) {
        this.dimension = dimension;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.centerBlockX = (chunkX << 4) + 8;
        this.centerBlockZ = (chunkZ << 4) + 8;
        this.entityCount = entityCount;
        this.blockEntityCount = blockEntityCount;
        this.topSummary = topSummary != null ? topSummary : "";
    }

    public String getDimension() { return dimension; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public int getCenterBlockX() { return centerBlockX; }
    public int getCenterBlockZ() { return centerBlockZ; }
    public int getEntityCount() { return entityCount; }
    public int getBlockEntityCount() { return blockEntityCount; }
    public String getTopSummary() { return topSummary; }
}
