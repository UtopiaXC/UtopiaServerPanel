package com.utopiaxc.utopiaserverpanel.spi.map;

/**
 * Listener notified when chunks change in-game so a future map renderer or integration
 * can mark tiles dirty without coupling {@code core} to chunk classes.
 */
@FunctionalInterface
public interface DirtyChunkListener {
    void onChunkDirty(String dimension, int chunkX, int chunkZ, long timestampMs);
}

