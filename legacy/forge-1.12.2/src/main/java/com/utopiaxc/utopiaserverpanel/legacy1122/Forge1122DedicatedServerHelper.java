package com.utopiaxc.utopiaserverpanel.legacy1122;

import com.utopiaxc.utopiaserverpanel.adapter.model.ServerPropertiesSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.util.ServerPropertiesReader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Isolated helper for dedicated-server-only properties on Forge 1.12.2.
 * Keeping {@link DedicatedServer} and {@code @SideOnly(Side.SERVER)} methods out of
 * {@link Forge1122GameAdapter} avoids client-side classloading failures on integrated servers.
 */
final class Forge1122DedicatedServerHelper {

    private Forge1122DedicatedServerHelper() {
    }

    static ServerPropertiesSnapshot readProperties(MinecraftServer server, Path serverDir) {
        if (!(server instanceof DedicatedServer)) {
            return null;
        }
        DedicatedServer ds = (DedicatedServer) server;
        Properties raw = ServerPropertiesReader.loadRawServerProperties(serverDir);
        ServerPropertiesSnapshot snap = new ServerPropertiesSnapshot();

        snap.setLevelSeed(raw.getProperty("level-seed", ""));
        snap.setLevelType(raw.getProperty("level-type", "default"));
        snap.setGenerateStructures(ds.canStructuresSpawn());
        int viewDist = parseInt(raw.getProperty("view-distance", "10"), 10);
        snap.setViewDistance(viewDist);
        snap.setSimulationDistance(viewDist);
        snap.setMaxBuildHeight(server.getBuildLimit());
        snap.setMaxWorldSize(server.getMaxWorldSize());

        snap.setGamemode(server.getGameType() != null ? server.getGameType().getName() : "survival");
        snap.setDifficulty(server.getDifficulty() != null
                ? server.getDifficulty().name().toLowerCase(Locale.ROOT)
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
        snap.setMaxTickTime(ds.getMaxTickTime());
        snap.setWhiteList(Boolean.parseBoolean(raw.getProperty("white-list", "false")));
        snap.setPlayerIdleTimeout(server.getMaxPlayerIdleMinutes());
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
}

