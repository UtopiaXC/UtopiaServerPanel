package com.utopiaxc.utopiaserverpanel.adapter.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.adapter.model.PlayerInfoSnapshot;
import com.utopiaxc.utopiaserverpanel.logging.PanelLog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Reads offline player stats ({@code stats/<uuid>.json}) and NBT state ({@code playerdata/<uuid>.dat})
 * uniformly across legacy (1.7.10~1.12.2) and modern (1.13~1.21+) world formats.
 */
public final class CoreWorldDataReader {
    private static final Gson GSON = new Gson();

    private CoreWorldDataReader() {}

    public static PlayerInfoSnapshot readOfflinePlayer(Path worldDir, UUID uuid) {
        PlayerInfoSnapshot info = new PlayerInfoSnapshot();
        info.setSupported(true);
        info.setOnline(false);

        if (worldDir == null || uuid == null) {
            return info;
        }

        // 1. Read death count from stats/<uuid>.json
        try {
            Path statsFile = worldDir.resolve("stats").resolve(uuid.toString() + ".json");
            if (Files.isRegularFile(statsFile)) {
                String content = new String(Files.readAllBytes(statsFile), StandardCharsets.UTF_8);
                JsonObject statsRoot = GSON.fromJson(content, JsonObject.class);
                if (statsRoot != null) {
                    if (statsRoot.has("stats") && statsRoot.get("stats").isJsonObject()) {
                        JsonObject stats = statsRoot.getAsJsonObject("stats");
                        if (stats.has("minecraft:custom") && stats.get("minecraft:custom").isJsonObject()) {
                            JsonObject custom = stats.getAsJsonObject("minecraft:custom");
                            int deaths = custom.has("minecraft:deaths") ? custom.get("minecraft:deaths").getAsInt() : 0;
                            info.setDeathCount(Integer.valueOf(deaths));
                        }
                    } else if (statsRoot.has("stat.deaths")) {
                        // Legacy MC 1.7.10 ~ 1.12.2 flat stats schema
                        info.setDeathCount(Integer.valueOf(statsRoot.get("stat.deaths").getAsInt()));
                    }
                }
            }
        } catch (Exception e) {
            PanelLog.get().debug("Failed to read offline stats for {}: {}", uuid, e.getMessage());
        }

        // 2. Read position, dimension, gamemode, health from playerdata/<uuid>.dat
        try {
            Path playerDat = worldDir.resolve("playerdata").resolve(uuid.toString() + ".dat");
            NbtLiteReader.OfflinePlayerNbt nbt = NbtLiteReader.readPlayerDat(playerDat);
            if (nbt != null) {
                info.setLastPosition(nbt.getPosition());
                info.setLastDimension(nbt.getDimension());
                info.setLastGamemode(nbt.getGamemode());
                info.setLastHealth(nbt.getHealth());
            }
        } catch (Exception e) {
            PanelLog.get().debug("Could not read playerdata for {}: {}", uuid, e.getMessage());
        }

        return info;
    }
}

