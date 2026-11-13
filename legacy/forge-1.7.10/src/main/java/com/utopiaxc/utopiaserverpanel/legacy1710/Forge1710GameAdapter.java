package com.utopiaxc.utopiaserverpanel.legacy1710;

import com.utopiaxc.utopiaserverpanel.adapter.GameAdapter;
import com.utopiaxc.utopiaserverpanel.adapter.model.OnlinePlayerSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.PlayerInfoSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.Position;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerPropertiesSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerStatusSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.util.CoreWorldDataReader;
import com.utopiaxc.utopiaserverpanel.spi.map.PlayerPositionSnapshot;
import cpw.mods.fml.common.Loader;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.StatList;
import net.minecraft.stats.StatisticsFile;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Forge 1.7.10 implementation of {@link GameAdapter}.
 */
public final class Forge1710GameAdapter implements GameAdapter {
    private final MinecraftServer server;
    private final Path configDir;
    private final long startTime;
    private final Queue<Runnable> mainThreadQueue = new ConcurrentLinkedQueue<Runnable>();

    public Forge1710GameAdapter(MinecraftServer server, Path configDir, long startTime) {
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

    public void drainMainThreadTasks() {
        Runnable task;
        while ((task = mainThreadQueue.poll()) != null) {
            try {
                task.run();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public String getPlatformName() {
        return "Forge 1.7.10";
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
        WorldServer overworld = server.worldServerForDimension(0);
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
        return Forge1710DedicatedServerHelper.readProperties(server, getServerDirectory());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<OnlinePlayerSnapshot> getOnlinePlayers() {
        List<OnlinePlayerSnapshot> list = new ArrayList<OnlinePlayerSnapshot>();
        List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : players) {
            list.add(new OnlinePlayerSnapshot(
                    player.getUniqueID().toString(),
                    player.getCommandSenderName(),
                    dimensionName(player.dimension),
                    player.theItemInWorldManager.getGameType().getName(),
                    player.getHealth(),
                    player.getMaxHealth(),
                    new Position((int) player.posX, (int) player.posY, (int) player.posZ)
            ));
        }
        return list;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<PlayerPositionSnapshot> getRealtimePlayerPositions() {
        List<PlayerPositionSnapshot> list = new ArrayList<PlayerPositionSnapshot>();
        List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : players) {
            list.add(new PlayerPositionSnapshot(
                    player.getUniqueID().toString(),
                    player.getCommandSenderName(),
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
    @SuppressWarnings("unchecked")
    public PlayerInfoSnapshot getPlayerInfo(String playerUuid) {
        UUID uuid;
        try {
            uuid = UUID.fromString(playerUuid);
        } catch (IllegalArgumentException e) {
            return PlayerInfoSnapshot.unsupported("Invalid UUID");
        }

        List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : players) {
            if (uuid.equals(player.getUniqueID())) {
                PlayerInfoSnapshot info = new PlayerInfoSnapshot();
                info.setSupported(true);
                info.setOnline(true);
                info.setName(player.getCommandSenderName());
                info.setDimension(dimensionName(player.dimension));
                info.setGamemode(player.theItemInWorldManager.getGameType().getName());
                info.setHealth(player.getHealth());
                info.setMaxHealth(player.getMaxHealth());
                info.setPosition(new Position((int) player.posX, (int) player.posY, (int) player.posZ));
                StatisticsFile stats = player.func_147099_x();
                if (stats != null) {
                    info.setDeathCount(Integer.valueOf(stats.writeStat(StatList.deathsStat)));
                }
                return info;
            }
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
            mainThreadQueue.offer(task);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getCommandCompletions(String partialCommand) {
        if (partialCommand == null || partialCommand.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<?> raw = server.getPossibleCompletions(server, partialCommand);
            List<String> out = new ArrayList<String>();
            if (raw != null) {
                for (Object item : raw) {
                    if (item != null) {
                        out.add(item.toString());
                    }
                }
            }
            return out;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean isModLoaded(String modId) {
        return modId != null && Loader.isModLoaded(modId);
    }
}
