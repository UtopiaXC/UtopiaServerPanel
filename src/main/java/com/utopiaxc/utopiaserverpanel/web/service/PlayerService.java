package com.utopiaxc.utopiaserverpanel.web.service;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.cache.PlayerDataCache;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.BindingMapper;

import java.util.Map;

/**
 * Service for querying player game data.
 * Uses the adapter layer (via cache) to get data from the game server.
 */
public final class PlayerService {

    private PlayerService() {}

    /**
     * Get player game data for a specific panel user (by their bound player UUID).
     * First checks the cache, then refreshes if needed.
     *
     * @param userId the panel user ID
     * @return JsonObject with player info, or null if user has no binding
     */
    public static JsonObject getPlayerDataForUser(int userId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var bindingMapper = session.getMapper(BindingMapper.class);
            Map<String, Object> binding = bindingMapper.findByUserId(userId);
            if (binding == null) return null;

            String playerUuid = (String) binding.get("playerUuid");
            String playerName = (String) binding.get("playerName");
            if (playerUuid == null) return null;

            // Try cache first, then refresh
            PlayerDataCache cache = PlayerDataCache.getInstance();
            JsonObject cached = cache.getPlayerData(playerUuid);
            if (cached != null) {
                cached.addProperty("playerName", playerName);
                return cached;
            }

            // Force refresh
            JsonObject fresh = cache.refreshPlayer(playerUuid);
            if (fresh != null) {
                fresh.addProperty("playerName", playerName);
            }
            return fresh;
        });
    }
}
