package com.utopiaxc.utopiaserverpanel.adapter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.storage.LevelResource;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

/**
 * NeoForge-specific implementation of {@link GameAdapter}.
 * Queries the Minecraft server using NeoForge/vanilla APIs.
 */
public class NeoForgeAdapter implements GameAdapter {
    private static final Gson GSON = new Gson();
    private static final SystemInfo SYSTEM_INFO = new SystemInfo();
    private static final HardwareAbstractionLayer HARDWARE = SYSTEM_INFO.getHardware();

    private final MinecraftServer server;
    private final long startTime;

    private long[] prevSystemTicks;
    private long[][] prevCoreTicks;

    public NeoForgeAdapter(MinecraftServer server, long startTime) {
        this.server = server;
        this.startTime = startTime;
    }

    @Override
    public JsonObject getServerStatus() {
        JsonObject status = new JsonObject();
        status.addProperty("onlinePlayers", server.getPlayerCount());
        status.addProperty("maxPlayers", server.getMaxPlayers());
        status.addProperty("motd", server.getMotd());
        status.addProperty("version", server.getServerVersion());
        status.addProperty("worldName", server.getWorldData().getLevelName());

        long[] times = server.getTickTimesNanos();
        double tickTimeMs = 0.0;
        if (times != null && times.length > 0) {
            tickTimeMs = java.util.Arrays.stream(times).average().orElse(0.0) / 1_000_000.0;
        }
        double tps = 1000.0 / Math.max(tickTimeMs, server.tickRateManager().millisecondsPerTick());
        status.addProperty("tps", tps);
        status.addProperty("tickTimeMs", tickTimeMs);
        return status;
    }

    @Override
    public JsonObject getServerProperties() {
        JsonObject properties = new JsonObject();
        if (!(server instanceof DedicatedServer ds)) return properties;
        DedicatedServerProperties props = ds.getProperties();

        // Raw server.properties for level-type
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
        } catch (Exception ignored) {}

        // World
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

        // Gameplay
        properties.addProperty("gamemode", props.gamemode.getName());
        properties.addProperty("difficulty", props.difficulty.getKey());
        properties.addProperty("hardcore", props.hardcore);
        properties.addProperty("pvp", props.pvp);
        properties.addProperty("allowFlight", props.allowFlight);
        properties.addProperty("allowNether", props.allowNether);
        properties.addProperty("spawnProtection", props.spawnProtection);

        // Network & Security
        properties.addProperty("onlineMode", props.onlineMode);
        properties.addProperty("serverIp", props.serverIp.isEmpty() ? "0.0.0.0" : props.serverIp);
        properties.addProperty("serverPort", props.serverPort);
        properties.addProperty("maxTickTime", props.maxTickTime);
        properties.addProperty("whiteList", props.whiteList.get());
        properties.addProperty("playerIdleTimeout", props.playerIdleTimeout.get());
        properties.addProperty("maxPlayers", props.maxPlayers);

        // Whitelist player names
        properties.add("whitelistPlayers", getWhitelistedPlayers());

        return properties;
    }

    @Override
    public JsonArray getOnlinePlayerList() {
        JsonArray list = new JsonArray();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            JsonObject p = new JsonObject();
            p.addProperty("uuid", player.getStringUUID());
            p.addProperty("name", player.getName().getString());
            p.addProperty("dimension", player.level().dimension().location().toString());
            p.addProperty("gamemode", player.gameMode.getGameModeForPlayer().getName());
            p.addProperty("health", player.getHealth());
            p.addProperty("maxHealth", player.getMaxHealth());
            JsonObject pos = new JsonObject();
            pos.addProperty("x", (int) player.getX());
            pos.addProperty("y", (int) player.getY());
            pos.addProperty("z", (int) player.getZ());
            p.add("position", pos);
            list.add(p);
        }
        return list;
    }

    @Override
    public JsonObject getPlayerInfo(String playerUuid) {
        JsonObject info = new JsonObject();

        // Try to find online player
        UUID uuid;
        try {
            uuid = UUID.fromString(playerUuid);
        } catch (IllegalArgumentException e) {
            info.addProperty("supported", false);
            info.addProperty("error", "Invalid UUID");
            return info;
        }

        ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(uuid);
        if (onlinePlayer != null) {
            // Online player
            info.addProperty("online", true);
            info.addProperty("name", onlinePlayer.getName().getString());
            info.addProperty("dimension", onlinePlayer.level().dimension().location().toString());
            info.addProperty("gamemode", onlinePlayer.gameMode.getGameModeForPlayer().getName());
            info.addProperty("health", onlinePlayer.getHealth());
            info.addProperty("maxHealth", onlinePlayer.getMaxHealth());

            JsonObject pos = new JsonObject();
            pos.addProperty("x", (int) onlinePlayer.getX());
            pos.addProperty("y", (int) onlinePlayer.getY());
            pos.addProperty("z", (int) onlinePlayer.getZ());
            info.add("position", pos);

            // Death count from stats
            int deaths = onlinePlayer.getStats().getValue(Stats.CUSTOM, Stats.DEATHS);
            info.addProperty("deathCount", deaths);
        } else {
            // Offline player - try to read stats file
            info.addProperty("online", false);
            readOfflinePlayerStats(uuid, info);
        }

        info.addProperty("supported", true);
        return info;
    }

    private void readOfflinePlayerStats(UUID uuid, JsonObject info) {
        try {
            // Read stats file
            Path statsDir = server.getWorldPath(LevelResource.PLAYER_STATS_DIR);
            Path statsFile = statsDir.resolve(uuid.toString() + ".json");
            if (Files.exists(statsFile)) {
                String content = Files.readString(statsFile);
                JsonObject statsRoot = GSON.fromJson(content, JsonObject.class);
                JsonObject stats = statsRoot.has("stats") ? statsRoot.getAsJsonObject("stats") : null;
                if (stats != null) {
                    JsonObject custom = stats.has("minecraft:custom") ? stats.getAsJsonObject("minecraft:custom") : null;
                    if (custom != null) {
                        info.addProperty("deathCount",
                                custom.has("minecraft:deaths") ? custom.get("minecraft:deaths").getAsInt() : 0);
                    }
                }
            }

            // Try to read player data for last position
            Path playerDataDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
            Path playerFile = playerDataDir.resolve(uuid.toString() + ".dat");
            if (Files.exists(playerFile)) {
                // Player .dat is NBT, reading requires MC NBT parser
                // We'll try to get last known position from the overworld
                try {
                    net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.NbtIo.readCompressed(playerFile, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                    if (tag.contains("Pos")) {
                        net.minecraft.nbt.ListTag posList = tag.getList("Pos", 6); // 6 = double
                        if (posList.size() >= 3) {
                            JsonObject pos = new JsonObject();
                            pos.addProperty("x", (int) posList.getDouble(0));
                            pos.addProperty("y", (int) posList.getDouble(1));
                            pos.addProperty("z", (int) posList.getDouble(2));
                            info.add("lastPosition", pos);
                        }
                    }
                    if (tag.contains("Dimension")) {
                        info.addProperty("lastDimension", tag.getString("Dimension"));
                    }
                    if (tag.contains("playerGameType")) {
                        int gameType = tag.getInt("playerGameType");
                        String[] modes = {"survival", "creative", "adventure", "spectator"};
                        info.addProperty("lastGamemode", gameType >= 0 && gameType < modes.length ? modes[gameType] : "unknown");
                    }
                    if (tag.contains("Health")) {
                        info.addProperty("lastHealth", tag.getFloat("Health"));
                    }
                } catch (Exception e) {
                    UtopiaServerPanel.LOGGER.debug("Could not read player data for {}: {}", uuid, e.getMessage());
                }
            }
        } catch (Exception e) {
            UtopiaServerPanel.LOGGER.debug("Failed to read offline stats for {}: {}", uuid, e.getMessage());
        }
    }

    @Override
    public JsonObject getSystemResources() {
        JsonObject resources = new JsonObject();
        Runtime runtime = Runtime.getRuntime();
        resources.addProperty("jvmMaxMemory", runtime.maxMemory());
        resources.addProperty("jvmUsedMemory", runtime.totalMemory() - runtime.freeMemory());

        GlobalMemory memory = HARDWARE.getMemory();
        resources.addProperty("systemTotalMemory", memory.getTotal());
        resources.addProperty("systemUsedMemory", memory.getTotal() - memory.getAvailable());

        CentralProcessor processor = HARDWARE.getProcessor();
        if (prevSystemTicks == null) {
            prevSystemTicks = processor.getSystemCpuLoadTicks();
            prevCoreTicks = processor.getProcessorCpuLoadTicks();
        }

        double systemCpuLoad = processor.getSystemCpuLoadBetweenTicks(prevSystemTicks) * 100;
        prevSystemTicks = processor.getSystemCpuLoadTicks();
        resources.addProperty("cpuLoad", systemCpuLoad);

        double[] coreLoads = processor.getProcessorCpuLoadBetweenTicks(prevCoreTicks);
        prevCoreTicks = processor.getProcessorCpuLoadTicks();
        JsonArray coreLoadsJson = new JsonArray();
        for (double load : coreLoads) {
            coreLoadsJson.add(load * 100);
        }
        resources.add("coreLoads", coreLoadsJson);

        return resources;
    }

    @Override
    public long getGameFolderSize() {
        try {
            File serverDir = server.getServerDirectory().toFile().getAbsoluteFile();
            return getFolderSize(serverDir);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public JsonObject getDiskInfo() {
        JsonObject disk = new JsonObject();
        try {
            File serverDir = server.getServerDirectory().toFile().getAbsoluteFile();
            disk.addProperty("diskTotalSpace", serverDir.getTotalSpace());
            disk.addProperty("diskFreeSpace", serverDir.getFreeSpace());
            disk.addProperty("diskUsableSpace", serverDir.getUsableSpace());
        } catch (Exception e) {
            disk.addProperty("diskTotalSpace", 0);
            disk.addProperty("diskFreeSpace", 0);
            disk.addProperty("diskUsableSpace", 0);
        }
        return disk;
    }

    @Override
    public void executeCommand(String command) {
        if (command == null || command.trim().isEmpty()) return;
        String cmd = command.trim();
        server.execute(() -> server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(), cmd));
    }

    @Override
    public JsonArray getCompletions(String partialCommand) {
        JsonArray completions = new JsonArray();
        if (partialCommand == null || partialCommand.isEmpty()) return completions;
        try {
            var dispatcher = server.getCommands().getDispatcher();
            var parseResults = dispatcher.parse(partialCommand, server.createCommandSourceStack());
            var suggestions = dispatcher.getCompletionSuggestions(parseResults).get();
            for (var suggestion : suggestions.getList()) {
                completions.add(suggestion.getText());
            }
        } catch (Exception ignored) {}
        return completions;
    }

    @Override
    public JsonArray getWhitelistedPlayers() {
        JsonArray players = new JsonArray();
        try {
            Path whitelistFile = server.getServerDirectory().resolve("whitelist.json");
            if (Files.exists(whitelistFile)) {
                String content = Files.readString(whitelistFile);
                JsonArray arr = GSON.fromJson(content, JsonArray.class);
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject obj = arr.get(i).getAsJsonObject();
                    if (obj.has("name")) {
                        players.add(obj.get("name").getAsString());
                    }
                }
            }
        } catch (Exception ignored) {}
        return players;
    }

    @Override
    public long getUptimeMillis() {
        if (server != null && server.overworld() != null) {
            return server.overworld().getGameTime() * 50L;
        }
        return System.currentTimeMillis() - startTime;
    }

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

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
}
