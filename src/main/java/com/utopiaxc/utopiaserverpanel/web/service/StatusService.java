package com.utopiaxc.utopiaserverpanel.web.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Service for collecting and serializing Minecraft server status data.
 */
public class StatusService {
    private static final Gson GSON = new Gson();
    private static final SystemInfo SYSTEM_INFO = new SystemInfo();
    private static final HardwareAbstractionLayer HARDWARE = SYSTEM_INFO.getHardware();
    
    private static long[] prevSystemTicks;
    private static long[][] prevCoreTicks;

    private static long getFolderSize(File folder) {
        long length = 0;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    length += file.length();
                } else {
                    length += getFolderSize(file);
                }
            }
        }
        return length;
    }

    /** Build the status data as a JsonObject (reusable by both HTTP and WS). */
    public static JsonObject getStatusObject(MinecraftServer minecraftServer, long startTime) {
        Runtime runtime = Runtime.getRuntime();
        long jvmMaxMemory = runtime.maxMemory();
        long jvmAllocatedMemory = runtime.totalMemory();
        long jvmFreeMemory = runtime.freeMemory();
        long jvmUsedMemory = jvmAllocatedMemory - jvmFreeMemory;

        GlobalMemory memory = HARDWARE.getMemory();
        long systemTotalMemory = memory.getTotal();

        JsonObject response = new JsonObject();
        response.addProperty("onlinePlayers", minecraftServer.getPlayerCount());
        response.addProperty("maxPlayers", minecraftServer.getMaxPlayers());
        response.addProperty("uptime", System.currentTimeMillis() - startTime);
        
        response.addProperty("jvmMaxMemory", jvmMaxMemory);
        response.addProperty("jvmUsedMemory", jvmUsedMemory);
        response.addProperty("systemTotalMemory", systemTotalMemory);
        
        response.addProperty("motd", minecraftServer.getMotd());
        response.addProperty("version", minecraftServer.getServerVersion());
        response.addProperty("worldName", minecraftServer.getWorldData().getLevelName());

        try {
            File serverDir = minecraftServer.getServerDirectory().toFile().getAbsoluteFile();
            response.addProperty("gameFolderSize", getFolderSize(serverDir));
        } catch (Exception e) {
            response.addProperty("gameFolderSize", 0);
        }
        
        try {
            File serverDir = minecraftServer.getServerDirectory().toFile().getAbsoluteFile();
            response.addProperty("diskTotalSpace", serverDir.getTotalSpace());
            response.addProperty("diskFreeSpace", serverDir.getFreeSpace());
            response.addProperty("diskUsableSpace", serverDir.getUsableSpace());
        } catch (Exception e) {
            response.addProperty("diskTotalSpace", 0);
            response.addProperty("diskFreeSpace", 0);
            response.addProperty("diskUsableSpace", 0);
        }

        CentralProcessor processor = HARDWARE.getProcessor();
        if (prevSystemTicks == null) {
            prevSystemTicks = processor.getSystemCpuLoadTicks();
            prevCoreTicks = processor.getProcessorCpuLoadTicks();
        }
        
        double systemCpuLoad = processor.getSystemCpuLoadBetweenTicks(prevSystemTicks) * 100;
        prevSystemTicks = processor.getSystemCpuLoadTicks();
        response.addProperty("cpuLoad", systemCpuLoad);
        
        double[] coreLoads = processor.getProcessorCpuLoadBetweenTicks(prevCoreTicks);
        prevCoreTicks = processor.getProcessorCpuLoadTicks();
        JsonArray coreLoadsJson = new JsonArray();
        for (double load : coreLoads) {
            coreLoadsJson.add(load * 100);
        }
        response.add("coreLoads", coreLoadsJson);
        
        long[] times = minecraftServer.getTickTimesNanos();
        double tickTimeMs = 0.0;
        if (times != null && times.length > 0) {
            tickTimeMs = java.util.Arrays.stream(times).average().orElse(0.0) / 1000000.0;
        }
        double tps = 1000.0 / Math.max(tickTimeMs, minecraftServer.tickRateManager().millisecondsPerTick());
        response.addProperty("tps", tps);

        JsonObject properties = new JsonObject();
        if (minecraftServer instanceof DedicatedServer ds) {
            DedicatedServerProperties props = ds.getProperties();

            // ── Raw server.properties for level-type (stored in private WorldDimensionData record) ──
            String levelType = "default";
            String levelSeed = "";
            try {
                Properties rawProps = new Properties();
                Path propsPath = ds.getServerDirectory().resolve("server.properties");
                try (FileReader reader = new FileReader(propsPath.toFile())) {
                    rawProps.load(reader);
                }
                levelType = rawProps.getProperty("level-type", "default");
                levelSeed = rawProps.getProperty("level-seed", "");
            } catch (Exception ignored) {
            }

            // ── World ──
            properties.addProperty("levelSeed", levelSeed.isEmpty() ? String.valueOf(props.worldOptions.seed()) : levelSeed);
            properties.addProperty("levelType", levelType);
            properties.addProperty("generateStructures", props.worldOptions.generateStructures());
            properties.addProperty("viewDistance", props.viewDistance);
            properties.addProperty("simulationDistance", props.simulationDistance);
            try {
                properties.addProperty("maxBuildHeight", ds.overworld().getMaxBuildHeight());
            } catch (Exception e) {
                properties.addProperty("maxBuildHeight", 320);
            }
            properties.addProperty("maxWorldSize", props.maxWorldSize);

            // ── Gameplay ──
            properties.addProperty("gamemode", props.gamemode.getName());
            properties.addProperty("difficulty", props.difficulty.getKey());
            properties.addProperty("hardcore", props.hardcore);
            properties.addProperty("pvp", props.pvp);
            properties.addProperty("allowFlight", props.allowFlight);
            properties.addProperty("allowNether", props.allowNether);
            properties.addProperty("spawnProtection", props.spawnProtection);

            // ── Network & Security ──
            properties.addProperty("onlineMode", props.onlineMode);
            properties.addProperty("serverIp", props.serverIp.isEmpty() ? "0.0.0.0" : props.serverIp);
            properties.addProperty("serverPort", props.serverPort);
            properties.addProperty("maxTickTime", props.maxTickTime);
            properties.addProperty("whiteList", props.whiteList.get());
            properties.addProperty("playerIdleTimeout", props.playerIdleTimeout.get());
            properties.addProperty("maxPlayers", props.maxPlayers);

            // Whitelist player names
            properties.add("whitelistPlayers", TerminalService.getWhitelistedPlayers(ds));
        }
        response.add("properties", properties);

        return response;
    }

    /** Wrap status data with WS message envelope {type, data} for WebSocket push. */
    public static String getStatusJson(MinecraftServer minecraftServer, long startTime) {
        JsonObject status = getStatusObject(minecraftServer, startTime);
        JsonObject wsMsg = new JsonObject();
        wsMsg.addProperty("type", "status");
        wsMsg.add("data", status);
        return GSON.toJson(wsMsg);
    }
}