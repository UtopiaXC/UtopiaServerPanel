package com.utopiaxc.utopiaserverpanel.legacy1122;

import com.utopiaxc.utopiaserverpanel.adapter.GameAdapter;
import com.utopiaxc.utopiaserverpanel.adapter.model.OnlinePlayerSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.PlayerInfoSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.Position;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerPropertiesSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerStatusSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.util.CoreWorldDataReader;
import com.utopiaxc.utopiaserverpanel.spi.map.PlayerPositionSnapshot;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.StatList;
import net.minecraft.stats.StatisticsManagerServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Forge 1.12.2 implementation of {@link GameAdapter}.
 */
public final class Forge1122GameAdapter implements GameAdapter {
    private final MinecraftServer server;
    private final Path configDir;
    private final long startTime;

    public Forge1122GameAdapter(MinecraftServer server, Path configDir, long startTime) {
        this.server = server;
        this.configDir = configDir;
        this.startTime = startTime;
    }

    private static double finiteOr(double val, double fallback) {
        return Double.isFinite(val) ? val : fallback;
    }

    private static String dimensionName(int dim) {
        if (dim == 0) return "minecraft:overworld";
        if (dim == -1) return "minecraft:the_nether";
        if (dim == 1) return "minecraft:the_end";
        return "dim:" + dim;
    }

    @Override
    public String getPlatformName() {
        return "Forge 1.12.2";
    }

    @Override
    public Path getServerDirectory() {
        File dir = server.getFile(".");
        return (dir != null ? dir : new File(".")).toPath().toAbsolutePath().normalize();
    }

    @Override
    public Path getConfigDirectory() {
        return configDir;
    }

    @Override
    public Path getWorldDirectory() {
        File worldDir = DimensionManager.getCurrentSaveRootDirectory();
        if (worldDir != null) {
            return worldDir.toPath().toAbsolutePath().normalize();
        }
        return getServerDirectory().resolve(server.getFolderName());
    }

    @Override
    public long getUptimeMillis() {
        WorldServer overworld = server.getWorld(0);
        if (overworld != null) {
            return overworld.getTotalWorldTime() * 50L;
        }
        return System.currentTimeMillis() - startTime;
    }

    @Override
    public ServerStatusSnapshot getServerStatus() {
        long[] times = server.tickTimeArray;
        double tickTimeMs = 0.0;
        if (times != null && times.length > 0) {
            long sum = 0L;
            for (long t : times) {
                sum += t;
            }
            tickTimeMs = ((double) sum / times.length) / 1000000.0;
        }
        tickTimeMs = Math.max(0.0, finiteOr(tickTimeMs, 0.0));
        double tps = finiteOr(1000.0 / Math.max(tickTimeMs, 50.0), 20.0);

        return new ServerStatusSnapshot(
                server.getCurrentPlayerCount(),
                server.getMaxPlayers(),
                server.getMOTD(),
                server.getMinecraftVersion(),
                server.getFolderName(),
                tps,
                tickTimeMs
        );
    }

    @Override
    public ServerPropertiesSnapshot getServerProperties() {
        if (!server.isDedicatedServer()) {
            return null;
        }
        return Forge1122DedicatedServerHelper.readProperties(server, getServerDirectory());
    }

    @Override
    public List<OnlinePlayerSnapshot> getOnlinePlayers() {
        List<OnlinePlayerSnapshot> list = new ArrayList<OnlinePlayerSnapshot>();
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            list.add(new OnlinePlayerSnapshot(
                    player.getUniqueID().toString(),
                    player.getName(),
                    dimensionName(player.dimension),
                    player.interactionManager.getGameType().getName(),
                    player.getHealth(),
                    player.getMaxHealth(),
                    new Position((int) player.posX, (int) player.posY, (int) player.posZ)
            ));
        }
        return list;
    }

    @Override
    public List<PlayerPositionSnapshot> getRealtimePlayerPositions() {
        List<PlayerPositionSnapshot> list = new ArrayList<PlayerPositionSnapshot>();
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            list.add(new PlayerPositionSnapshot(
                    player.getUniqueID().toString(),
                    player.getName(),
                    dimensionName(player.dimension),
                    player.posX,
                    player.posY,
                    player.posZ,
                    player.rotationYaw,
                    player.rotationPitch,
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

        EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(uuid);
        if (online != null) {
            PlayerInfoSnapshot info = new PlayerInfoSnapshot();
            info.setSupported(true);
            info.setOnline(true);
            info.setName(online.getName());
            info.setDimension(dimensionName(online.dimension));
            info.setGamemode(online.interactionManager.getGameType().getName());
            info.setHealth(online.getHealth());
            info.setMaxHealth(online.getMaxHealth());
            info.setPosition(new Position((int) online.posX, (int) online.posY, (int) online.posZ));
            StatisticsManagerServer stats = online.getStatFile();
            if (stats != null) {
                info.setDeathCount(Integer.valueOf(stats.readStat(StatList.DEATHS)));
            }
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
                server.getCommandManager().executeCommand(server, cmd);
            }
        });
    }

    @Override
    public void runOnServerThread(Runnable task) {
        if (task != null) {
            server.addScheduledTask(task);
        }
    }

    @Override
    public List<String> getCommandCompletions(String partialCommand) {
        if (partialCommand == null || partialCommand.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return server.getTabCompletions(server, partialCommand, BlockPos.ORIGIN, false);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean isModLoaded(String modId) {
        return modId != null && Loader.isModLoaded(modId);
    }
}
