package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.MonitorMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for accumulating performance data samples and periodically
 * flushing averaged snapshots to the database.
 * <p>
 * Called from {@link com.utopiaxc.utopiaserverpanel.web.cache.ServerStatusCache}
 * on every status refresh tick.
 */
public final class MonitorLogService {

    private MonitorLogService() {}

    // ── Accumulators ──
    private static double cpuSum = 0;
    private static long memJvmSum = 0;
    private static long memJvmMaxSum = 0;
    private static long memSysUsedSum = 0;
    private static long memSysTotalSum = 0;
    private static double tpsSum = 0;
    private static long diskUsedSum = 0;
    private static long diskTotalSum = 0;
    private static long gameFolderSum = 0;
    private static long onlineCountSum = 0;
    private static int sampleCount = 0;

    private static long lastFlushTime = System.currentTimeMillis() / 1000;
    private static long lastCleanupTime = System.currentTimeMillis() / 1000;

    /**
     * Accumulate a sample from the current status poll.
     * When enough time has elapsed (>= monitorInterval), flush the average to DB.
     */
    public static synchronized void addSample(
            double cpu, long memJvm, long memJvmMax,
            long memSysUsed, long memSysTotal,
            double tps, long diskUsed, long diskTotal,
            long gameFolder, int onlineCount) {

        if (!MonitorConfigService.isEnabled()) return;

        cpuSum += cpu;
        memJvmSum += memJvm;
        memJvmMaxSum += memJvmMax;
        memSysUsedSum += memSysUsed;
        memSysTotalSum += memSysTotal;
        tpsSum += tps;
        diskUsedSum += diskUsed;
        diskTotalSum += diskTotal;
        gameFolderSum += gameFolder;
        onlineCountSum += onlineCount;
        sampleCount++;

        long now = System.currentTimeMillis() / 1000;
        int intervalSeconds = MonitorConfigService.getIntervalSeconds();

        // Flush if enough time has passed
        if (now - lastFlushTime >= intervalSeconds && sampleCount > 0) {
            flushToDb(now);
        }

        // Periodic cleanup: every hour
        if (now - lastCleanupTime >= 3600) {
            cleanupOldLogs(now);
            lastCleanupTime = now;
        }
    }

    private static void flushToDb(long now) {
        Map<String, Object> log = new HashMap<>();
        log.put("ts", now);
        log.put("cpu", Math.round(cpuSum / sampleCount * 100.0) / 100.0);
        log.put("memJvm", memJvmSum / sampleCount);
        log.put("memJvmMax", memJvmMaxSum / sampleCount);
        log.put("memSysUsed", memSysUsedSum / sampleCount);
        log.put("memSysTotal", memSysTotalSum / sampleCount);
        log.put("tps", Math.round(tpsSum / sampleCount * 100.0) / 100.0);
        log.put("diskUsed", diskUsedSum / sampleCount);
        log.put("diskTotal", diskTotalSum / sampleCount);
        log.put("gameFolder", gameFolderSum / sampleCount);
        log.put("onlineCount", (int) (onlineCountSum / sampleCount));

        try {
            MyBatisFactory.doWork(session -> {
                MonitorMapper mapper = session.getMapper(MonitorMapper.class);
                mapper.insertPerfLog(log);
            });
            // Broadcast real-time update to web clients
            com.utopiaxc.utopiaserverpanel.web.controller.WebSocketController.broadcastMonitorLog(log);
        } catch (Exception e) {
            UtopiaServerPanel.LOGGER.warn("Failed to insert perf log", e);
        }

        // Reset accumulators
        cpuSum = 0;
        memJvmSum = 0;
        memJvmMaxSum = 0;
        memSysUsedSum = 0;
        memSysTotalSum = 0;
        tpsSum = 0;
        diskUsedSum = 0;
        diskTotalSum = 0;
        gameFolderSum = 0;
        onlineCountSum = 0;
        sampleCount = 0;
        lastFlushTime = now;
    }

    private static void cleanupOldLogs(long now) {
        int retentionDays = MonitorConfigService.getRetentionDays();
        long cutoff = now - (long) retentionDays * 24 * 3600;

        try {
            MyBatisFactory.doWork(session -> {
                MonitorMapper mapper = session.getMapper(MonitorMapper.class);
                int perfDeleted = mapper.deleteOldPerfLogs(cutoff);
                int evtDeleted = mapper.deleteOldPlayerEvents(cutoff);
                int lcDeleted = mapper.deleteOldLifecycle(cutoff);
                if (perfDeleted > 0 || evtDeleted > 0 || lcDeleted > 0) {
                    UtopiaServerPanel.LOGGER.info("Cleaned up {} perf logs, {} player events, {} lifecycle events older than {} days",
                            perfDeleted, evtDeleted, lcDeleted, retentionDays);
                }
            });
        } catch (Exception e) {
            UtopiaServerPanel.LOGGER.warn("Failed to cleanup old monitor logs", e);
        }
    }
}
