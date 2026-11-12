package com.utopiaxc.utopiaserverpanel.platform.shared;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.utopiaxc.utopiaserverpanel.adapter.GameAdapter;
import com.utopiaxc.utopiaserverpanel.adapter.model.OnlinePlayerSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.PlayerInfoSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.Position;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerPropertiesSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerStatusSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.util.CoreWorldDataReader;
import com.utopiaxc.utopiaserverpanel.adapter.util.ServerPropertiesReader;
import com.utopiaxc.utopiaserverpanel.spi.map.PlayerPositionSnapshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.ArrayList;
//? if mc >= 1.20.3 {
import java.util.Arrays;
//?}
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Unified modern Mojang-mapped {@link GameAdapter} spanning Minecraft 1.16.5 through 26.3
 * across Forge, NeoForge, and Fabric.
 */
public final class ModernGameAdapter implements GameAdapter {
    private final MinecraftServer server;
    private final Path configDir;
    private final long startTime;

    public ModernGameAdapter(MinecraftServer server, Path configDir, long startTime) {
        this.server = server;
        this.configDir = configDir;
        this.startTime = startTime;
    }

    private static double finiteOr(double val, double fallback) {
        return Double.isFinite(val) ? val : fallback;
    }

    @Override
    public String getPlatformName() {
        return BuildConstants.LOADER_DISPLAY_NAME;
    }

    @Override
    public Path getServerDirectory() {
        //? if mc >= 1.21 {
        return server.getServerDirectory().toAbsolutePath().normalize();
        //?} else {
        return server.getServerDirectory().toPath().toAbsolutePath().normalize();
        //?}
    }

    @Override
    public Path getConfigDirectory() {
        return configDir;
    }

    @Override
    public Path getWorldDirectory() {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
    }

    @Override
    public long getUptimeMillis() {
        if (server != null && server.overworld() != null) {
            return server.overworld().getGameTime() * 50L;
        }
        return System.currentTimeMillis() - startTime;
    }

    @Override
    public ServerStatusSnapshot getServerStatus() {
        double tickTimeMs;
        double mspt;
        //? if mc >= 1.20.3 {
        long[] times = server.getTickTimesNanos();
        double avgMs = 0.0;
        if (times != null && times.length > 0) {
            avgMs = Arrays.stream(times).average().orElse(0.0) / 1_000_000.0;
        }
        tickTimeMs = Math.max(0.0, finiteOr(avgMs, 0.0));
        mspt = server.tickRateManager().millisecondsPerTick();
        if (!Double.isFinite(mspt) || mspt <= 0.0) {
            mspt = 50.0;
        }
        //?} else {
        tickTimeMs = Math.max(0.0, finiteOr(server.getAverageTickTime(), 0.0));
        mspt = 50.0;
        //?}
        double tps = finiteOr(1000.0 / Math.max(tickTimeMs, mspt), 20.0);

        return new ServerStatusSnapshot(
                server.getPlayerCount(),
                server.getMaxPlayers(),
                server.getMotd(),
                server.getServerVersion(),
                server.getWorldData().getLevelName(),
                tps,
                tickTimeMs
        );
    }

    @Override
    public ServerPropertiesSnapshot getServerProperties() {
        if (!(server instanceof DedicatedServer)) {
            return null;
        }
        DedicatedServer ds = (DedicatedServer) server;
        DedicatedServerProperties props = ds.getProperties();
        Properties rawProps = ServerPropertiesReader.loadRawServerProperties(getServerDirectory());
        String rawSeed = rawProps.getProperty("level-seed", "");
        String rawLevelType = rawProps.getProperty("level-type", "default");

        ServerPropertiesSnapshot snap = new ServerPropertiesSnapshot();
        //? if mc >= 1.19.3 {
        snap.setLevelSeed(rawSeed.isEmpty() ? String.valueOf(props.worldOptions.seed()) : rawSeed);
        snap.setLevelType(rawLevelType);
        snap.setGenerateStructures(props.worldOptions.generateStructures());
        //?} else {
        snap.setLevelSeed(rawSeed.isEmpty() ? String.valueOf(ds.getWorldData().worldGenSettings().seed()) : rawSeed);
        snap.setLevelType(rawLevelType);
        snap.setGenerateStructures(ds.getWorldData().worldGenSettings().generateFeatures());
        //?}
        //? if mc >= 1.21.11 {
        snap.setViewDistance(props.viewDistance.get());
        snap.setSimulationDistance(props.simulationDistance.get());
        //?} else if mc >= 1.18 {
        snap.setViewDistance(props.viewDistance);
        snap.setSimulationDistance(props.simulationDistance);
        //?} else {
        snap.setViewDistance(props.viewDistance);
        snap.setSimulationDistance(props.viewDistance);
        //?}
        try {
            //? if mc >= 1.21.2 {
            snap.setMaxBuildHeight(ds.overworld().getMaxY() + 1);
            //?} else {
            snap.setMaxBuildHeight(ds.overworld().getMaxBuildHeight());
            //?}
        } catch (Exception e) {
            snap.setMaxBuildHeight(320);
        }
        snap.setMaxWorldSize(props.maxWorldSize);

        //? if mc >= 1.21.11 {
        snap.setGamemode(props.gameMode.get().getName());
        snap.setDifficulty(props.difficulty.get().getSerializedName());
        snap.setHardcore(props.hardcore);
        boolean pvpEnabled = ds.overworld() != null
                ? Boolean.TRUE.equals(ds.overworld().getGameRules().get(net.minecraft.world.level.gamerules.GameRules.PVP))
                : Boolean.parseBoolean(rawProps.getProperty("pvp", "true"));
        boolean netherEnabled = ds.overworld() != null
                ? Boolean.TRUE.equals(ds.overworld().getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS))
                : Boolean.parseBoolean(rawProps.getProperty("allow-nether", "true"));
        snap.setPvp(pvpEnabled);
        snap.setAllowFlight(Boolean.TRUE.equals(props.allowFlight.get()));
        snap.setAllowNether(netherEnabled);
        snap.setSpawnProtection(props.spawnProtection.get());
        //?} else {
        snap.setGamemode(props.gamemode.getName());
        snap.setDifficulty(props.difficulty.getKey());
        snap.setHardcore(props.hardcore);
        snap.setPvp(props.pvp);
        snap.setAllowFlight(props.allowFlight);
        snap.setAllowNether(props.allowNether);
        snap.setSpawnProtection(props.spawnProtection);
        //?}

        snap.setOnlineMode(props.onlineMode);
        snap.setServerIp(props.serverIp.isEmpty() ? "0.0.0.0" : props.serverIp);
        snap.setServerPort(props.serverPort);
        snap.setMaxTickTime(props.maxTickTime);
        snap.setWhiteList(props.whiteList.get());
        snap.setPlayerIdleTimeout(props.playerIdleTimeout.get());
        //? if mc >= 1.21.11 {
        snap.setMaxPlayers(props.maxPlayers.get());
        //?} else {
        snap.setMaxPlayers(props.maxPlayers);
        //?}
        snap.setWhitelistPlayers(ServerPropertiesReader.readWhitelistPlayers(getServerDirectory()));
        return snap;
    }

    private static String playerDimensionId(ServerPlayer player) {
        //? if mc >= 1.21.11 {
        return player.level().dimension().identifier().toString();
        //?} else if mc >= 1.20 {
        return player.level().dimension().location().toString();
        //?} else {
        return player.getLevel().dimension().location().toString();
        //?}
    }

    @Override
    public List<OnlinePlayerSnapshot> getOnlinePlayers() {
        List<OnlinePlayerSnapshot> list = new ArrayList<OnlinePlayerSnapshot>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            list.add(new OnlinePlayerSnapshot(
                    player.getStringUUID(),
                    player.getName().getString(),
                    playerDimensionId(player),
                    player.gameMode.getGameModeForPlayer().getName(),
                    player.getHealth(),
                    player.getMaxHealth(),
                    new Position((int) player.getX(), (int) player.getY(), (int) player.getZ())
            ));
        }
        return list;
    }

    @Override
    public List<PlayerPositionSnapshot> getRealtimePlayerPositions() {
        List<PlayerPositionSnapshot> list = new ArrayList<PlayerPositionSnapshot>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            //? if mc >= 1.17 {
            float yaw = player.getYRot();
            float pitch = player.getXRot();
            //?} else {
            float yaw = player.yRot;
            float pitch = player.xRot;
            //?}
            list.add(new PlayerPositionSnapshot(
                    player.getStringUUID(),
                    player.getName().getString(),
                    playerDimensionId(player),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    yaw,
                    pitch,
                    player.getHealth()
            ));
        }
        return list;
    }

    @Override
    public PlayerInfoSnapshot getPlayerInfo(String playerUuid) {
        UUID uuid;
        try {
            uuid = UUID.fromString(playerUuid);
        } catch (IllegalArgumentException e) {
            return PlayerInfoSnapshot.unsupported("Invalid UUID");
        }

        ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(uuid);
        if (onlinePlayer != null) {
            PlayerInfoSnapshot info = new PlayerInfoSnapshot();
            info.setSupported(true);
            info.setOnline(true);
            info.setName(onlinePlayer.getName().getString());
            info.setDimension(playerDimensionId(onlinePlayer));
            info.setGamemode(onlinePlayer.gameMode.getGameModeForPlayer().getName());
            info.setHealth(onlinePlayer.getHealth());
            info.setMaxHealth(onlinePlayer.getMaxHealth());
            info.setPosition(new Position(
                    (int) onlinePlayer.getX(),
                    (int) onlinePlayer.getY(),
                    (int) onlinePlayer.getZ()
            ));
            info.setDeathCount(Integer.valueOf(onlinePlayer.getStats().getValue(Stats.CUSTOM, Stats.DEATHS)));
            return info;
        }

        return CoreWorldDataReader.readOfflinePlayer(getWorldDirectory(), uuid);
    }

    @Override
    public void executeCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }
        final String cmd = command.trim();
        runOnServerThread(new Runnable() {
            @Override
            public void run() {
                //? if mc >= 1.19 {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
                //?} else {
                server.getCommands().performCommand(server.createCommandSourceStack(), cmd);
                //?}
            }
        });
    }

    @Override
    public void runOnServerThread(Runnable task) {
        if (task != null) {
            server.execute(task);
        }
    }

    @Override
    public List<String> getCommandCompletions(String partialCommand) {
        List<String> completions = new ArrayList<String>();
        if (partialCommand == null || partialCommand.isEmpty()) {
            return completions;
        }
        try {
            CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
            ParseResults<CommandSourceStack> parseResults = dispatcher.parse(
                    partialCommand, server.createCommandSourceStack());
            Suggestions suggestions = dispatcher.getCompletionSuggestions(parseResults).get();
            for (Suggestion suggestion : suggestions.getList()) {
                completions.add(suggestion.getText());
            }
        } catch (Exception ignored) {
        }
        return completions;
    }

    @Override
    public boolean isModLoaded(String modId) {
        if (modId == null) {
            return false;
        }
        //? if loader == 'fabric' {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(modId);
        //?} else if loader == 'neoforge' {
        return net.neoforged.fml.ModList.get().isLoaded(modId);
        //?} else if mc >= 26.1 {
        return net.minecraftforge.fml.ModList.isLoaded(modId);
        //?} else {
        return net.minecraftforge.fml.ModList.get().isLoaded(modId);
        //?}
    }
}

