package com.utopiaxc.utopiaserverpanel.web.cache;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.adapter.AdapterJsonMapper;
import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;
import com.utopiaxc.utopiaserverpanel.adapter.GameAdapter;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerPropertiesSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerStatusSnapshot;
import com.utopiaxc.utopiaserverpanel.config.PanelConfig;
import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
import com.utopiaxc.utopiaserverpanel.system.SystemMonitor;
import com.utopiaxc.utopiaserverpanel.system.SystemResourcesSnapshot;
import com.utopiaxc.utopiaserverpanel.web.service.MonitorLogService;

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

    private final AtomicReference<JsonObject> cachedStatus = new AtomicReference<JsonObject>(new JsonObject());
    private ScheduledExecutorService scheduler;

    private ServerStatusCache() {}

    public static ServerStatusCache getInstance() { return INSTANCE; }

    /**
     * Start the background polling thread.
     * Must be called after AdapterRegistry is initialized.
     */
    public void start() {
        int intervalMs = PanelConfig.getBroadcastIntervalMs();
        refresh();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "UtopiaPanel-StatusCache");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::refresh, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        PanelLog.get().info("ServerStatusCache started with interval {} ms", Integer.valueOf(intervalMs));
    }

    /** Stop the background polling thread and clear cached data. */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        cachedStatus.set(new JsonObject());
        PanelLog.get().info("ServerStatusCache stopped");
    }

    /** Get the latest cached status snapshot. Never blocks on MC server thread. */
    public JsonObject getSnapshot() {
        return cachedStatus.get();
    }

    /** Refresh the cache by polling the adapter. Called by the background thread. */
    private void refresh() {
        try {
            GameAdapter adapter = AdapterRegistry.getAdapter();
            ServerStatusSnapshot serverStatus = adapter.getServerStatus();
            SystemResourcesSnapshot sys = SystemMonitor.capture(adapter.getServerDirectory());
            ServerPropertiesSnapshot props = adapter.getServerProperties();

            JsonObject status = AdapterJsonMapper.buildCombinedStatus(
                    serverStatus,
                    adapter.getUptimeMillis(),
                    sys,
                    props
            );
            cachedStatus.set(status);

            // Feed monitor log accumulator
            try {
                double cpu = Math.max(0.0, sys.getCpuLoad());
                double tps = serverStatus != null ? serverStatus.getTps() : 20.0;
                int onlineCount = serverStatus != null ? serverStatus.getOnlinePlayers() : 0;
                long diskTotal = sys.getDiskTotalSpace();
                long diskUsed = diskTotal - sys.getDiskFreeSpace();

                MonitorLogService.addSample(
                        cpu,
                        sys.getJvmUsedMemory(),
                        sys.getJvmMaxMemory(),
                        sys.getSystemUsedMemory(),
                        sys.getSystemTotalMemory(),
                        tps,
                        diskUsed,
                        diskTotal,
                        sys.getGameFolderSize(),
                        onlineCount
                );
            } catch (Exception ignored) {
                // Don't let monitoring failures affect the status cache
            }
        } catch (Exception e) {
            PanelLog.get().warn("ServerStatusCache refresh error", e);
        }
    }
}
