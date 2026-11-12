package com.utopiaxc.utopiaserverpanel.adapter.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerPropertiesSnapshot;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Shared filesystem readers for {@code server.properties} and server whitelist files
 * across all Minecraft versions (modern {@code whitelist.json} and legacy 1.7 {@code white-list.txt}).
 */
public final class ServerPropertiesReader {
    private static final Gson GSON = new Gson();

    private ServerPropertiesReader() {}

    public static Properties loadRawServerProperties(Path serverDir) {
        Properties props = new Properties();
        if (serverDir == null) return props;
        Path propsPath = serverDir.resolve("server.properties");
        if (!Files.isRegularFile(propsPath)) return props;
        try (BufferedReader reader = Files.newBufferedReader(propsPath, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException ignored) {}
        return props;
    }

    public static void populateFromRawProperties(Path serverDir, ServerPropertiesSnapshot target) {
        if (target == null) return;
        Properties raw = loadRawServerProperties(serverDir);
        if (!raw.isEmpty()) {
            target.setLevelSeed(raw.getProperty("level-seed", target.getLevelSeed()));
            target.setLevelType(raw.getProperty("level-type", target.getLevelType()));
            target.setGenerateStructures(parseBool(raw.getProperty("generate-structures"), target.isGenerateStructures()));
            target.setViewDistance(parseInt(raw.getProperty("view-distance"), target.getViewDistance()));
            target.setSimulationDistance(parseInt(raw.getProperty("simulation-distance"), target.getSimulationDistance()));
            target.setMaxBuildHeight(parseInt(raw.getProperty("max-build-height"), target.getMaxBuildHeight()));
            target.setMaxWorldSize(parseInt(raw.getProperty("max-world-size"), target.getMaxWorldSize()));

            target.setGamemode(raw.getProperty("gamemode", target.getGamemode()));
            target.setDifficulty(raw.getProperty("difficulty", target.getDifficulty()));
            target.setHardcore(parseBool(raw.getProperty("hardcore"), target.isHardcore()));
            target.setPvp(parseBool(raw.getProperty("pvp"), target.isPvp()));
            target.setAllowFlight(parseBool(raw.getProperty("allow-flight"), target.isAllowFlight()));
            target.setAllowNether(parseBool(raw.getProperty("allow-nether"), target.isAllowNether()));
            target.setSpawnProtection(parseInt(raw.getProperty("spawn-protection"), target.getSpawnProtection()));

            target.setOnlineMode(parseBool(raw.getProperty("online-mode"), target.isOnlineMode()));
            target.setServerIp(raw.getProperty("server-ip", ""));
            target.setServerPort(parseInt(raw.getProperty("server-port"), target.getServerPort()));
            target.setMaxTickTime(parseLong(raw.getProperty("max-tick-time"), target.getMaxTickTime()));
            target.setWhiteList(parseBool(raw.getProperty("white-list"), target.isWhiteList()));
            target.setPlayerIdleTimeout(parseInt(raw.getProperty("player-idle-timeout"), target.getPlayerIdleTimeout()));
            target.setMaxPlayers(parseInt(raw.getProperty("max-players"), target.getMaxPlayers()));
        }
        target.setWhitelistPlayers(readWhitelistPlayers(serverDir));
    }

    public static List<String> readWhitelistPlayers(Path serverDir) {
        List<String> players = new ArrayList<String>();
        if (serverDir == null) return players;

        Path jsonFile = serverDir.resolve("whitelist.json");
        if (Files.isRegularFile(jsonFile)) {
            try {
                String content = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);
                JsonArray arr = GSON.fromJson(content, JsonArray.class);
                if (arr != null) {
                    for (int i = 0; i < arr.size(); i++) {
                        JsonElement el = arr.get(i);
                        if (el.isJsonObject()) {
                            JsonObject obj = el.getAsJsonObject();
                            if (obj.has("name") && !obj.get("name").isJsonNull()) {
                                players.add(obj.get("name").getAsString());
                            }
                        } else if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                            players.add(el.getAsString());
                        }
                    }
                }
                return players;
            } catch (Exception ignored) {}
        }

        // Legacy 1.7.x fallback
        Path txtFile = serverDir.resolve("white-list.txt");
        if (Files.isRegularFile(txtFile)) {
            try {
                for (String line : Files.readAllLines(txtFile, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        players.add(trimmed);
                    }
                }
            } catch (IOException ignored) {}
        }
        return players;
    }

    private static boolean parseBool(String v, boolean fallback) {
        if (v == null || v.trim().isEmpty()) return fallback;
        return Boolean.parseBoolean(v.trim());
    }

    private static int parseInt(String v, int fallback) {
        if (v == null || v.trim().isEmpty()) return fallback;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String v, long fallback) {
        if (v == null || v.trim().isEmpty()) return fallback;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

