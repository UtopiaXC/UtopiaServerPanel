package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.TokenMapper;

/**
 * Service for managing monitoring configuration stored in server_config table.
 * <p>
 * Config keys:
 * <ul>
 *   <li>monitor_enabled — "true"/"false" (default: true)</li>
 *   <li>monitor_interval — seconds between perf log snapshots (default: 60)</li>
 *   <li>monitor_retention_days — how many days to keep logs (default: 30)</li>
 *   <li>monitor_show_player_names — show player names in frontend (default: true)</li>
 * </ul>
 */
public final class MonitorConfigService {

    private MonitorConfigService() {}

    // ── Cached values (refreshed on get/set) ──

    private static volatile boolean enabled = true;
    private static volatile int intervalSeconds = 60;
    private static volatile int retentionDays = 30;
    private static volatile boolean showPlayerNames = true;
    private static volatile boolean loaded = false;

    /** Load all monitor config from DB into cache. */
    public static void loadAll() {
        MyBatisFactory.doWork(session -> {
            TokenMapper mapper = session.getMapper(TokenMapper.class);
            String val;

            val = mapper.getConfig("monitor_enabled");
            enabled = val == null || !"false".equals(val);

            val = mapper.getConfig("monitor_interval");
            intervalSeconds = val != null ? Integer.parseInt(val) : 60;
            if (intervalSeconds < 10) intervalSeconds = 10;

            val = mapper.getConfig("monitor_retention_days");
            retentionDays = val != null ? Integer.parseInt(val) : 30;
            if (retentionDays < 1) retentionDays = 1;

            val = mapper.getConfig("monitor_show_player_names");
            showPlayerNames = val == null || !"false".equals(val);
        });
        loaded = true;
    }

    public static boolean isEnabled() {
        if (!loaded) loadAll();
        return enabled;
    }

    public static int getIntervalSeconds() {
        if (!loaded) loadAll();
        return intervalSeconds;
    }

    public static int getRetentionDays() {
        if (!loaded) loadAll();
        return retentionDays;
    }

    public static boolean isShowPlayerNames() {
        if (!loaded) loadAll();
        return showPlayerNames;
    }

    /** Save all monitor settings to DB and update cache. */
    public static void saveAll(boolean newEnabled, int newInterval, int newRetention, boolean newShowNames) {
        if (newInterval < 10) newInterval = 10;
        if (newRetention < 1) newRetention = 1;

        final int safeInterval = newInterval;
        final int safeRetention = newRetention;

        MyBatisFactory.doWork(session -> {
            TokenMapper mapper = session.getMapper(TokenMapper.class);
            mapper.setConfig("monitor_enabled", String.valueOf(newEnabled));
            mapper.setConfig("monitor_interval", String.valueOf(safeInterval));
            mapper.setConfig("monitor_retention_days", String.valueOf(safeRetention));
            mapper.setConfig("monitor_show_player_names", String.valueOf(newShowNames));
        });

        enabled = newEnabled;
        intervalSeconds = safeInterval;
        retentionDays = safeRetention;
        showPlayerNames = newShowNames;
    }
}
