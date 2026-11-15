package com.utopiaxc.utopiaserverpanel.spi.profiler;

/**
 * Per-dimension runtime load statistics (loaded chunks, entities, block entities).
 */
public final class DimensionHotspotDto {
    private final String dimension;
    private final int loadedChunks;
    private final int entityCount;
    private final int blockEntityCount;
    private final int playerCount;

    public DimensionHotspotDto(String dimension, int loadedChunks, int entityCount, int blockEntityCount, int playerCount) {
        this.dimension = dimension;
        this.loadedChunks = loadedChunks;
        this.entityCount = entityCount;
        this.blockEntityCount = blockEntityCount;
        this.playerCount = playerCount;
    }

    public String getDimension() { return dimension; }
    public int getLoadedChunks() { return loadedChunks; }
    public int getEntityCount() { return entityCount; }
    public int getBlockEntityCount() { return blockEntityCount; }
    public int getPlayerCount() { return playerCount; }
}
