package com.utopiaxc.utopiaserverpanel.web.service;

/**
 * Facade over SettingsService for monitor log retention, sample interval, and player name visibility.
 */
public final class MonitorConfigService {

    private MonitorConfigService() {}

    public static void loadAll() {
        SettingsService.reload();
    }

    public static boolean isEnabled() {
        return SettingsService.getBool("monitor_enabled", true);
    }

    public static int getIntervalSeconds() {
        return Math.max(10, SettingsService.getInt("monitor_interval", 60));
    }

    public static int getRetentionDays() {
        return Math.max(1, SettingsService.getInt("monitor_retention_days", 30));
    }

    public static boolean isShowPlayerNames() {
        return SettingsService.getBool("monitor_show_player_names", true);
    }

    public static void saveAll(boolean enabled, int intervalSeconds, int retentionDays, boolean showPlayerNames) {
        int safeInterval = Math.max(10, intervalSeconds);
        int safeRetention = Math.max(1, retentionDays);
        java.util.Map<String, String> updates = new java.util.LinkedHashMap<String, String>();
        updates.put("monitor_enabled", String.valueOf(enabled));
        updates.put("monitor_interval", String.valueOf(safeInterval));
        updates.put("monitor_retention_days", String.valueOf(safeRetention));
        updates.put("monitor_show_player_names", String.valueOf(showPlayerNames));
        SettingsService.setMany(updates);
    }
}
