package com.utopiaxc.utopiaserverpanel.web.cache;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.Config;
import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cache for per-player game data.
 * <p>
 * Uses a reference-counting mechanism: polling starts when the first WebSocket
 * client connects and stops when the last one disconnects. This avoids
 * unnecessary game server queries when nobody is watching.
 * </p>
 * <p>
 * The cache is bounded to prevent memory leaks — entries are evicted after a
 * configurable TTL and the total number of cached players is capped.
 * </p>
 */
public final class PlayerDataCache {
    private static final PlayerDataCache INSTANCE = new PlayerDataCache();
    private static final int MAX_CACHED_PLAYERS = 200;

    private final ConcurrentHashMap<String, CachedEntry> cache = new ConcurrentHashMap<>();
    private final AtomicInteger refCount = new AtomicInteger(0);
    private ScheduledExecutorService scheduler;

    private PlayerDataCache() {}

    public static PlayerDataCache getInstance() { return INSTANCE; }

    /**
     * Increment the reference count. Starts polling if this is the first reference.
     */
    public void addRef() {
        int count = refCount.incrementAndGet();
        if (count == 1) {
            startPolling();
        }
    }

    /**
     * Decrement the reference count. Stops polling and clears cache if count reaches zero.
     */
    public void removeRef() {
        int count = refCount.decrementAndGet();
        if (count <= 0) {
            refCount.set(0);
            stopPolling();
            cache.clear();
        }
    }

    /**
     * Get cached player data by UUID.
     * @return cached JsonObject or null if not cached
     */
    public JsonObject getPlayerData(String playerUuid) {
        CachedEntry entry = cache.get(playerUuid);
        if (entry == null) return null;
        // Check TTL: 2x broadcast interval
        long ttl = Config.BROADCAST_INTERVAL.getAsInt() * 2L;
        if (System.currentTimeMillis() - entry.timestamp > ttl) {
            cache.remove(playerUuid);
            return null;
        }
        return entry.data;
    }

    /**
     * Force-refresh a specific player's cache entry.
     */
    public JsonObject refreshPlayer(String playerUuid) {
        try {
            JsonObject data = AdapterRegistry.getAdapter().getPlayerInfo(playerUuid);
            if (data != null) {
                // Enforce map size limit
                if (cache.size() >= MAX_CACHED_PLAYERS && !cache.containsKey(playerUuid)) {
                    evictOldest();
                }
                cache.put(playerUuid, new CachedEntry(data));
            }
            return data;
        } catch (Exception e) {
            UtopiaServerPanel.LOGGER.debug("Failed to refresh player cache for {}: {}", playerUuid, e.getMessage());
            return null;
        }
    }

    /** Clear all cache and reset ref count. Used during shutdown. */
    public void shutdown() {
        stopPolling();
        cache.clear();
        refCount.set(0);
    }

    private void startPolling() {
        if (scheduler != null && !scheduler.isShutdown()) return;
        int intervalMs = Config.BROADCAST_INTERVAL.getAsInt();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "UtopiaPanel-PlayerCache");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::refreshAll, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
//        UtopiaServerPanel.LOGGER.info("PlayerDataCache polling started");
    }

    private void stopPolling() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
//        UtopiaServerPanel.LOGGER.info("PlayerDataCache polling stopped");
    }

    private void refreshAll() {
        try {
            var onlinePlayers = AdapterRegistry.getAdapter().getOnlinePlayerList();
            for (int i = 0; i < onlinePlayers.size(); i++) {
                JsonObject player = onlinePlayers.get(i).getAsJsonObject();
                String uuid = player.get("uuid").getAsString();
                JsonObject full = AdapterRegistry.getAdapter().getPlayerInfo(uuid);
                if (full != null) {
                    if (cache.size() >= MAX_CACHED_PLAYERS && !cache.containsKey(uuid)) {
                        evictOldest();
                    }
                    cache.put(uuid, new CachedEntry(full));
                }
            }
        } catch (Exception e) {
            UtopiaServerPanel.LOGGER.debug("PlayerDataCache refresh error: {}", e.getMessage());
        }
    }

    private void evictOldest() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        for (var entry : cache.entrySet()) {
            if (entry.getValue().timestamp < oldestTime) {
                oldestTime = entry.getValue().timestamp;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            cache.remove(oldestKey);
        }
    }

    private static class CachedEntry {
        final JsonObject data;
        final long timestamp;

        CachedEntry(JsonObject data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
