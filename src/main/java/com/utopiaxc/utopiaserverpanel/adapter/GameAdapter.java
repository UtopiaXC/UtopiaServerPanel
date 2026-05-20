package com.utopiaxc.utopiaserverpanel.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Abstraction layer for game server data queries.
 * <p>
 * Currently only NeoForge is implemented. Future adapters (Forge, Fabric, etc.)
 * can implement this interface and be registered via {@link AdapterRegistry}.
 * Methods that are not supported by a particular adapter should return
 * a result with {@code "supported": false}.
 * </p>
 */
public interface GameAdapter {

    /**
     * Get core server status: player count, version, world name, TPS, tick time, MOTD.
     */
    JsonObject getServerStatus();

    /**
     * Get server properties (from server.properties and DedicatedServerProperties).
     */
    JsonObject getServerProperties();

    /**
     * Get the list of online players with basic info.
     * Returns a JsonArray of player objects.
     */
    JsonArray getOnlinePlayerList();

    /**
     * Get detailed data for a specific player by UUID.
     * Includes: online status, dimension, coordinates, health, gamemode, death count.
     * Works for both online and offline players (offline data read from stats files).
     */
    JsonObject getPlayerInfo(String playerUuid);

    /**
     * Get JVM and system resource info: memory, CPU, disk.
     */
    JsonObject getSystemResources();

    /**
     * Get the game folder size in bytes.
     */
    long getGameFolderSize();

    /**
     * Get disk space info for the server directory.
     */
    JsonObject getDiskInfo();

    /**
     * Execute a server command.
     */
    void executeCommand(String command);

    /**
     * Get command completions for tab-complete.
     */
    JsonArray getCompletions(String partialCommand);

    /**
     * Get whitelisted player names.
     */
    JsonArray getWhitelistedPlayers();

    /**
     * Get the server uptime in milliseconds.
     */
    long getUptimeMillis();

    /**
     * Get the platform name (e.g. "NeoForge", "Fabric", "Forge").
     */
    String getPlatformName();
}
