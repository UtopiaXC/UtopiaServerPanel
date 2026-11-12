package com.utopiaxc.utopiaserverpanel.spi.map;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe registry decoupling map tile sources and dirty chunk listeners from {@code core}.
 */
public final class MapIntegrationRegistry {
    private static volatile MapTileProvider activeTileProvider;
    private static final List<DirtyChunkListener> DIRTY_LISTENERS = new CopyOnWriteArrayList<DirtyChunkListener>();

    private MapIntegrationRegistry() {}

    public static void setTileProvider(MapTileProvider provider) {
        activeTileProvider = provider;
    }

    public static MapTileProvider getTileProvider() {
        return activeTileProvider;
    }

    public static void addDirtyChunkListener(DirtyChunkListener listener) {
        if (listener != null) {
            DIRTY_LISTENERS.add(listener);
        }
    }

    public static void removeDirtyChunkListener(DirtyChunkListener listener) {
        if (listener != null) {
            DIRTY_LISTENERS.remove(listener);
        }
    }

    public static void notifyChunkDirty(String dimension, int chunkX, int chunkZ) {
        if (DIRTY_LISTENERS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (DirtyChunkListener listener : DIRTY_LISTENERS) {
            listener.onChunkDirty(dimension, chunkX, chunkZ, now);
        }
    }

    public static void clear() {
        activeTileProvider = null;
        DIRTY_LISTENERS.clear();
    }
}

