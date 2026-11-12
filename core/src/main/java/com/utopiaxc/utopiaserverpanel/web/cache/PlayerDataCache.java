package com.utopiaxc.utopiaserverpanel.web.cache;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.adapter.AdapterJsonMapper;
import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;
import com.utopiaxc.utopiaserverpanel.adapter.model.OnlinePlayerSnapshot;
import com.utopiaxc.utopiaserverpanel.config.PanelConfig;
import com.utopiaxc.utopiaserverpanel.logging.PanelLog;

import java.util.List;
import java.util.Map;
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

    private final ConcurrentHashMap<String, CachedEntry> cache = new ConcurrentHashMap<String, CachedEntry>();
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

    private static int getBroadcastIntervalMs() {
        return PanelConfig.getBroadcastIntervalMs();
    }

    /**
     * Get cached player data by UUID.
     * @return cached JsonObject or null if not cached
     */
    public JsonObject getPlayerData(String playerUuid) {
        CachedEntry entry = cache.get(playerUuid);
        if (entry == null) return null;
        // Check TTL: 2x broadcast interval
        long ttl = getBroadcastIntervalMs() * 2L;
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
            JsonObject data = AdapterJsonMapper.toPlayerInfoJson(AdapterRegistry.getAdapter().getPlayerInfo(playerUuid));
            if (data != null) {
                // Enforce map size limit
                if (cache.size() >= MAX_CACHED_PLAYERS && !cache.containsKey(playerUuid)) {
                    evictOldest();
                }
                cache.put(playerUuid, new CachedEntry(data));
            }
            return data;
        } catch (Exception e) {
            PanelLog.get().debug("Failed to refresh player cache for {}: {}", playerUuid, e.getMessage());
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
        int intervalMs = getBroadcastIntervalMs();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "UtopiaPanel-PlayerCache");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::refreshAll, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void stopPolling() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void refreshAll() {
        try {
            List<OnlinePlayerSnapshot> onlinePlayers = AdapterRegistry.getAdapter().getOnlinePlayers();
            for (OnlinePlayerSnapshot player : onlinePlayers) {
                String uuid = player.getUuid();
                if (uuid == null) continue;
                JsonObject full = AdapterJsonMapper.toPlayerInfoJson(AdapterRegistry.getAdapter().getPlayerInfo(uuid));
                if (full != null) {
                    if (cache.size() >= MAX_CACHED_PLAYERS && !cache.containsKey(uuid)) {
                        evictOldest();
                    }
                    cache.put(uuid, new CachedEntry(full));
                }
            }
        } catch (Exception e) {
            PanelLog.get().debug("PlayerDataCache refresh error: {}", e.getMessage());
        }
    }

    private void evictOldest() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        for (Map.Entry<String, CachedEntry> entry : cache.entrySet()) {
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
