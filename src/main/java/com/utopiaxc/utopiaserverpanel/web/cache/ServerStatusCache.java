package com.utopiaxc.utopiaserverpanel.web.cache;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.Config;
import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;
import com.utopiaxc.utopiaserverpanel.adapter.GameAdapter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe cache for global server status data.
 * <p>
 * A single background thread polls the game adapter at the configured broadcast interval
 * and stores the latest snapshot. All reads go through {@link #getSnapshot()} which
 * never hits the Minecraft server thread directly.
 * </p>
 */
public final class ServerStatusCache {
    private static final ServerStatusCache INSTANCE = new ServerStatusCache();

    private final AtomicReference<JsonObject> cachedStatus = new AtomicReference<>(new JsonObject());
    private ScheduledExecutorService scheduler;

    private ServerStatusCache() {}

    public static ServerStatusCache getInstance() { return INSTANCE; }

    /**
     * Start the background polling thread.
     * Must be called after AdapterRegistry is initialized.
     */
    public void start() {
        int intervalMs = Config.BROADCAST_INTERVAL.getAsInt();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "UtopiaPanel-StatusCache");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::refresh, 0, intervalMs, TimeUnit.MILLISECONDS);
        UtopiaServerPanel.LOGGER.info("ServerStatusCache started with interval {} ms", intervalMs);
    }

    /** Stop the background polling thread and clear cached data. */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        cachedStatus.set(new JsonObject());
        UtopiaServerPanel.LOGGER.info("ServerStatusCache stopped");
    }

    /** Get the latest cached status snapshot. Never blocks on MC server thread. */
    public JsonObject getSnapshot() {
        return cachedStatus.get();
    }

    /** Refresh the cache by polling the adapter. Called by the background thread. */
    private void refresh() {
        try {
            GameAdapter adapter = AdapterRegistry.getAdapter();

            JsonObject status = adapter.getServerStatus();
            status.addProperty("uptime", adapter.getUptimeMillis());

            // System resources
            JsonObject resources = adapter.getSystemResources();
            for (String key : resources.keySet()) {
                status.add(key, resources.get(key));
            }

            // Disk info
            JsonObject disk = adapter.getDiskInfo();
            for (String key : disk.keySet()) {
                status.add(key, disk.get(key));
            }

            // Game folder size (expensive, but cached so only once per interval)
            long gameFolderSize = adapter.getGameFolderSize();
            status.addProperty("gameFolderSize", gameFolderSize);

            // Server properties
            status.add("properties", adapter.getServerProperties());

            cachedStatus.set(status);

            // Feed monitor log accumulator
            try {
                double cpu = status.has("cpuLoad") ? status.get("cpuLoad").getAsDouble() : 0;
                long memJvm = status.has("jvmUsedMemory") ? status.get("jvmUsedMemory").getAsLong() : 0;
                long memJvmMax = status.has("jvmMaxMemory") ? status.get("jvmMaxMemory").getAsLong() : 0;
                long memSysUsed = status.has("systemUsedMemory") ? status.get("systemUsedMemory").getAsLong() : 0;
                long memSysTotal = status.has("systemTotalMemory") ? status.get("systemTotalMemory").getAsLong() : 0;
                double tps = status.has("tps") ? status.get("tps").getAsDouble() : 20.0;
                long diskTotal = status.has("diskTotalSpace") ? status.get("diskTotalSpace").getAsLong() : 0;
                long diskFree = status.has("diskFreeSpace") ? status.get("diskFreeSpace").getAsLong() : 0;
                long diskUsed = diskTotal - diskFree;
                int onlineCount = status.has("onlinePlayers") ? status.get("onlinePlayers").getAsInt() : 0;

                com.utopiaxc.utopiaserverpanel.web.service.MonitorLogService.addSample(
                        cpu, memJvm, memJvmMax, memSysUsed, memSysTotal,
                        tps, diskUsed, diskTotal, gameFolderSize, onlineCount);
            } catch (Exception ignored) {
                // Don't let monitoring failures affect the status cache
            }
        } catch (Exception e) {
            UtopiaServerPanel.LOGGER.warn("ServerStatusCache refresh error", e);
        }
    }
}
