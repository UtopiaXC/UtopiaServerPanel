package com.utopiaxc.utopiaserverpanel.legacy1710;

import com.utopiaxc.utopiaserverpanel.adapter.model.ServerPropertiesSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.util.ServerPropertiesReader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Isolated helper for dedicated-server-only properties on Forge 1.7.10.
 * Keeping {@link DedicatedServer} and {@code @SideOnly(Side.SERVER)} methods out of
 * {@link Forge1710GameAdapter} avoids {@link NoClassDefFoundError} on integrated client servers.
 */
final class Forge1710DedicatedServerHelper {

    private Forge1710DedicatedServerHelper() {
    }

    static ServerPropertiesSnapshot readProperties(MinecraftServer server, Path serverDir) {
        if (!(server instanceof DedicatedServer)) {
            return null;
        }
        DedicatedServer ds = (DedicatedServer) server;
        Properties raw = ServerPropertiesReader.loadRawServerProperties(serverDir);
        ServerPropertiesSnapshot snap = new ServerPropertiesSnapshot();

        snap.setLevelSeed(raw.getProperty("level-seed", ""));
        snap.setLevelType(raw.getProperty("level-type", "DEFAULT"));
        snap.setGenerateStructures(Boolean.parseBoolean(raw.getProperty("generate-structures", "true")));
        int viewDist = parseInt(raw.getProperty("view-distance", "10"), 10);
        snap.setViewDistance(viewDist);
        snap.setSimulationDistance(viewDist);
        snap.setMaxBuildHeight(server.getBuildLimit());
        snap.setMaxWorldSize(parseInt(raw.getProperty("max-world-size", "29999984"), 29999984));

        snap.setGamemode(server.getGameType() != null ? server.getGameType().getName() : "survival");
        snap.setDifficulty(server.func_147135_j() != null
                ? server.func_147135_j().name().toLowerCase(Locale.ROOT)
                : "normal");
        snap.setHardcore(server.isHardcore());
        snap.setPvp(server.isPVPEnabled());
        snap.setAllowFlight(server.isFlightAllowed());
        snap.setAllowNether(server.getAllowNether());
        snap.setSpawnProtection(server.getSpawnProtectionSize());

        snap.setOnlineMode(server.isServerInOnlineMode());
        String ip = server.getServerHostname();
        snap.setServerIp(ip == null || ip.isEmpty() ? "0.0.0.0" : ip);
        snap.setServerPort(ds.getPort() > 0 ? ds.getPort() : parseInt(raw.getProperty("server-port", "25565"), 25565));
        snap.setMaxTickTime(parseLong(raw.getProperty("max-tick-time", "60000"), 60000L));
        snap.setWhiteList(Boolean.parseBoolean(raw.getProperty("white-list", "false")));
        snap.setPlayerIdleTimeout(server.func_143007_ar());
        snap.setMaxPlayers(server.getMaxPlayers());
        snap.setWhitelistPlayers(ServerPropertiesReader.readWhitelistPlayers(serverDir));
        return snap;
    }

    private static int parseInt(String val, int fallback) {
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long parseLong(String val, long fallback) {
        try {
            return Long.parseLong(val.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}

