package com.utopiaxc.utopiaserverpanel.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.utopiaxc.utopiaserverpanel.adapter.model.OnlinePlayerSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.PlayerInfoSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.Position;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerPropertiesSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerStatusSnapshot;
import com.utopiaxc.utopiaserverpanel.system.SystemResourcesSnapshot;

import java.util.List;

/**
 * Converts strongly-typed core & adapter snapshots into the JSON contract expected
 * by the REST API and WebSocket clients.
 */
public final class AdapterJsonMapper {

    private AdapterJsonMapper() {}

    public static JsonObject buildCombinedStatus(
            ServerStatusSnapshot status,
            long uptimeMillis,
            SystemResourcesSnapshot sys,
            ServerPropertiesSnapshot props
    ) {
        JsonObject out = new JsonObject();
        if (status != null) {
            out.addProperty("onlinePlayers", status.getOnlinePlayers());
            out.addProperty("maxPlayers", status.getMaxPlayers());
            out.addProperty("motd", status.getMotd());
            out.addProperty("version", status.getVersion());
            out.addProperty("worldName", status.getWorldName());
            out.addProperty("tps", status.getTps());
            out.addProperty("tickTimeMs", status.getTickTimeMs());
        }
        out.addProperty("uptime", uptimeMillis);

        if (sys != null) {
            out.addProperty("jvmMaxMemory", sys.getJvmMaxMemory());
            out.addProperty("jvmUsedMemory", sys.getJvmUsedMemory());
            out.addProperty("systemTotalMemory", sys.getSystemTotalMemory());
            out.addProperty("systemUsedMemory", sys.getSystemUsedMemory());
            out.addProperty("cpuLoad", sys.getCpuLoad());

            JsonArray cores = new JsonArray();
            for (double load : sys.getCoreLoads()) {
                cores.add(new JsonPrimitive(Double.valueOf(load)));
            }
            out.add("coreLoads", cores);

            out.addProperty("diskTotalSpace", sys.getDiskTotalSpace());
            out.addProperty("diskFreeSpace", sys.getDiskFreeSpace());
            out.addProperty("diskUsableSpace", sys.getDiskUsableSpace());
            out.addProperty("gameFolderSize", sys.getGameFolderSize());
        }

        out.add("properties", toPropertiesJson(props));
        return out;
    }

    public static JsonObject toPropertiesJson(ServerPropertiesSnapshot props) {
        JsonObject p = new JsonObject();
        if (props == null) {
            return p;
        }
        p.addProperty("levelSeed", props.getLevelSeed());
        p.addProperty("levelType", props.getLevelType());
        p.addProperty("generateStructures", props.isGenerateStructures());
        p.addProperty("viewDistance", props.getViewDistance());
        p.addProperty("simulationDistance", props.getSimulationDistance());
        p.addProperty("maxBuildHeight", props.getMaxBuildHeight());
        p.addProperty("maxWorldSize", props.getMaxWorldSize());

        p.addProperty("gamemode", props.getGamemode());
        p.addProperty("difficulty", props.getDifficulty());
        p.addProperty("hardcore", props.isHardcore());
        p.addProperty("pvp", props.isPvp());
        p.addProperty("allowFlight", props.isAllowFlight());
        p.addProperty("allowNether", props.isAllowNether());
        p.addProperty("spawnProtection", props.getSpawnProtection());

        p.addProperty("onlineMode", props.isOnlineMode());
        p.addProperty("serverIp", props.getServerIp());
        p.addProperty("serverPort", props.getServerPort());
        p.addProperty("maxTickTime", props.getMaxTickTime());
        p.addProperty("whiteList", props.isWhiteList());
        p.addProperty("playerIdleTimeout", props.getPlayerIdleTimeout());
        p.addProperty("maxPlayers", props.getMaxPlayers());

        JsonArray wl = new JsonArray();
        for (String name : props.getWhitelistPlayers()) {
            wl.add(new JsonPrimitive(name));
        }
        p.add("whitelistPlayers", wl);
        return p;
    }

    public static JsonArray toOnlinePlayersJson(List<OnlinePlayerSnapshot> players) {
        JsonArray arr = new JsonArray();
        if (players == null) return arr;
        for (OnlinePlayerSnapshot pl : players) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", pl.getUuid());
            o.addProperty("name", pl.getName());
            o.addProperty("dimension", pl.getDimension());
            o.addProperty("gamemode", pl.getGamemode());
            o.addProperty("health", pl.getHealth());
            o.addProperty("maxHealth", pl.getMaxHealth());
            if (pl.getPosition() != null) {
                o.add("position", toPositionJson(pl.getPosition()));
            }
            arr.add(o);
        }
        return arr;
    }

    public static JsonObject toPlayerInfoJson(PlayerInfoSnapshot info) {
        JsonObject o = new JsonObject();
        if (info == null) {
            o.addProperty("supported", false);
            return o;
        }
        if (!info.isSupported()) {
            o.addProperty("supported", false);
            if (info.getError() != null) o.addProperty("error", info.getError());
            return o;
        }
        o.addProperty("online", info.isOnline());
        if (info.isOnline()) {
            if (info.getName() != null) o.addProperty("name", info.getName());
            if (info.getDimension() != null) o.addProperty("dimension", info.getDimension());
            if (info.getGamemode() != null) o.addProperty("gamemode", info.getGamemode());
            if (info.getHealth() != null) o.addProperty("health", info.getHealth());
            if (info.getMaxHealth() != null) o.addProperty("maxHealth", info.getMaxHealth());
            if (info.getPosition() != null) o.add("position", toPositionJson(info.getPosition()));
            if (info.getDeathCount() != null) o.addProperty("deathCount", info.getDeathCount());
        } else {
            if (info.getDeathCount() != null) o.addProperty("deathCount", info.getDeathCount());
            if (info.getLastPosition() != null) o.add("lastPosition", toPositionJson(info.getLastPosition()));
            if (info.getLastDimension() != null) o.addProperty("lastDimension", info.getLastDimension());
            if (info.getLastGamemode() != null) o.addProperty("lastGamemode", info.getLastGamemode());
            if (info.getLastHealth() != null) o.addProperty("lastHealth", info.getLastHealth());
        }
        o.addProperty("supported", true);
        return o;
    }

    private static JsonObject toPositionJson(Position pos) {
        JsonObject p = new JsonObject();
        p.addProperty("x", pos.getX());
        p.addProperty("y", pos.getY());
        p.addProperty("z", pos.getZ());
        return p;
    }
}
