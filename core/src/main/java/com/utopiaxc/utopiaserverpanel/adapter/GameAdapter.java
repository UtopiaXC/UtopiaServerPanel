package com.utopiaxc.utopiaserverpanel.adapter;

import com.utopiaxc.utopiaserverpanel.adapter.model.OnlinePlayerSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.PlayerInfoSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerPropertiesSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerStatusSnapshot;
import com.utopiaxc.utopiaserverpanel.spi.map.MapChunkRenderData;
import com.utopiaxc.utopiaserverpanel.spi.map.MapDimensionInfo;
import com.utopiaxc.utopiaserverpanel.spi.map.MapMarkerDto;
import com.utopiaxc.utopiaserverpanel.spi.map.PlayerPositionSnapshot;
import com.utopiaxc.utopiaserverpanel.spi.mod.ModEntryDto;
import com.utopiaxc.utopiaserverpanel.spi.profiler.ProfilerHotspotSnapshot;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Platform & Minecraft-version abstraction SPI implemented by thin loader adapters
 * (NeoForge, Forge, Fabric, legacy 1.7.10/1.12.2, etc.).
 * <p>
 * System CPU/memory telemetry, disk metrics, directory sizes, config storage, log ring buffering,
 * offline stats/NBT file parsing, and web/DB logic are handled cleanly inside {@code core}.
 * </p>
 */
public interface GameAdapter {

    /** Human-readable loader/platform name (e.g., "NeoForge", "Forge", "Fabric"). */
    String getPlatformName();

    /** Server working directory containing server.properties, mods, world, etc. */
    Path getServerDirectory();

    /** Directory where config/database files should reside (typically serverDir/config). */
    Path getConfigDirectory();

    /** Active world folder path containing {@code stats/} and {@code playerdata/} (nullable). */
    Path getWorldDirectory();

    /** Server uptime in milliseconds. */
    long getUptimeMillis();

    /** Current server status snapshot (player counts, version, MOTD, TPS, MSPT). */
    ServerStatusSnapshot getServerStatus();

    /**
     * Runtime server properties snapshot, or {@code null} when running on non-dedicated /
     * unsupported environments (returns empty properties object to the frontend).
     */
    ServerPropertiesSnapshot getServerProperties();

    /** Currently online players. */
    List<OnlinePlayerSnapshot> getOnlinePlayers();

    /** Live (online) or disk-backed (offline) player summary for a given player UUID. */
    PlayerInfoSnapshot getPlayerInfo(String playerUuid);

    /** Dispatch a command string on the game server main thread. */
    void executeCommand(String command);

    /** Compute command tab-completions for the web terminal. */
    List<String> getCommandCompletions(String partialCommand);

    /**
     * Dispatch a runnable onto the game server main tick thread for safe world/mod mutation.
     */
    default void runOnServerThread(Runnable task) {
        if (task != null) {
            task.run();
        }
    }

    /**
     * High-frequency player coordinates for live map overlays.
     */
    default List<PlayerPositionSnapshot> getRealtimePlayerPositions() {
        return Collections.emptyList();
    }

    /**
     * Query whether a third-party mod is loaded in the active loader environment.
     */
    default boolean isModLoaded(String modId) {
        return false;
    }

    /** Enumerate all loaded server dimensions for the web map & profiler. */
    default List<MapDimensionInfo> getMapDimensions() {
        return Collections.emptyList();
    }

    /** List currently loaded chunk coordinates {@code [chunkX, chunkZ]} in a dimension. */
    default List<int[]> listLoadedChunkCoords(String dimension) {
        return Collections.emptyList();
    }

    /** Capture top-down 16x16 pixel & height data for a loaded chunk (must be called safely). */
    default MapChunkRenderData captureLiveChunkForMap(String dimension, int chunkX, int chunkZ) {
        return null;
    }

    /** Collect live entity & structure markers for the given dimension (isolated by callers). */
    default List<MapMarkerDto> collectLiveMapMarkers(String dimension) {
        return Collections.emptyList();
    }

    /** Collect entity, block-entity, and dimension hotspot statistics on demand. */
    default ProfilerHotspotSnapshot collectHotspotSnapshot() {
        return null;
    }

    /** Collect installed mod metadata reported by the active loader. */
    default List<ModEntryDto> getLoadedMods() {
        return Collections.emptyList();
    }

    /** Broadcast a chat message from a bound web user into in-game public chat. */
    default void broadcastPublicChat(String senderName, String message) {
        if (message == null || message.trim().isEmpty()) return;
        String prefix = (senderName != null && !senderName.trim().isEmpty()) ? senderName.trim() : "Web";
        executeCommand("say [Web] <" + prefix + "> " + message.replace('\n', ' ').replace('\r', ' '));
    }

    /** Send a direct notification or private message to an online player if present. */
    default void sendDirectPlayerMessage(String playerNameOrUuid, String message) {
        if (playerNameOrUuid == null || playerNameOrUuid.trim().isEmpty() || message == null) return;
        executeCommand("msg " + playerNameOrUuid.trim() + " " + message.replace('\n', ' ').replace('\r', ' '));
    }

    /** Teleport a player to exact coordinates in a dimension, returning true if dispatched. */
    default boolean teleportPlayerTo(String playerNameOrUuid, String dimension, double x, double y, double z) {
        return false;
    }

    /** Ask the server to reload its whitelist file if supported. */
    default void reloadServerWhitelist() {
        executeCommand("whitelist reload");
    }

    /** Gracefully stop the server so external supervisors / containers can restart it. */
    default void shutdownServer(String reason) {
        executeCommand("stop");
    }
}
