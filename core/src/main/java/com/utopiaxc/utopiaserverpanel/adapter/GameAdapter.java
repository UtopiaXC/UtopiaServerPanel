package com.utopiaxc.utopiaserverpanel.adapter;

import com.utopiaxc.utopiaserverpanel.adapter.model.OnlinePlayerSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.PlayerInfoSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerPropertiesSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerStatusSnapshot;
import com.utopiaxc.utopiaserverpanel.spi.map.PlayerPositionSnapshot;

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
}
