package com.utopiaxc.utopiaserverpanel.web.service;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.cache.PlayerDataCache;
import com.utopiaxc.utopiaserverpanel.web.db.model.PlayerBindingInfo;

/**
 * Service for querying bound player game data.
 */
public final class PlayerService {

    private PlayerService() {}

    public static JsonObject getPlayerDataForUser(int userId) {
        PlayerBindingInfo binding = BindingService.findByUserId(userId);
        if (binding == null || binding.getPlayerUuid() == null) return null;

        String playerUuid = binding.getPlayerUuid();
        String playerName = binding.getPlayerName();

        PlayerDataCache cache = PlayerDataCache.getInstance();
        JsonObject cached = cache.getPlayerData(playerUuid);
        if (cached != null) {
            cached.addProperty("playerName", playerName);
            cached.addProperty("playerUuid", playerUuid);
            return cached;
        }

        JsonObject fresh = cache.refreshPlayer(playerUuid);
        if (fresh != null) {
            fresh.addProperty("playerName", playerName);
            fresh.addProperty("playerUuid", playerUuid);
        }
        return fresh;
    }
}
