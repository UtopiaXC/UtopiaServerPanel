package com.utopiaxc.utopiaserverpanel.config;

import com.utopiaxc.utopiaserverpanel.logging.PanelLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform-independent panel configuration backed by {@code config/utopia_server_panel.toml}.
 * Replaces loader-specific {@code ModConfigSpec} so NeoForge, Forge, Fabric, and legacy servers
 * share identical configuration semantics with zero extra dependencies.
 */
public final class PanelConfig {
    public static final String FILE_NAME = "utopia_server_panel.toml";
    private static final String LEGACY_NEOFORGE_FILE_NAME = "utopia_server_panel-common.toml";

    private static final Object LOCK = new Object();
    private static volatile Path configPath;

    private static volatile int webPort = 55533;
    private static volatile int broadcastInterval = 3000;
    private static volatile int maxLogLines = 1000;
    private static volatile String minLogLevel = "INFO";
    private static volatile String setupToken = "";

    private PanelConfig() {}

    public static void initialize(Path configDir) {
        synchronized (LOCK) {
            if (configDir == null) return;
            try {
                Files.createDirectories(configDir);
            } catch (IOException e) {
                PanelLog.get().warn("Could not create config directory {}: {}", configDir, e.getMessage());
            }

            Path primary = configDir.resolve(FILE_NAME);
            Path legacy = configDir.resolve(LEGACY_NEOFORGE_FILE_NAME);
            configPath = primary;

            if (Files.exists(primary)) {
                loadFrom(primary);
            } else if (Files.exists(legacy)) {
                loadFrom(legacy);
                save();
            } else {
                save();
            }
        }
    }

    public static boolean isInitialized() {
        return configPath != null;
    }

    public static String getConfigDisplayPath() {
        return "config/" + FILE_NAME;
    }

    public static int getWebPort() {
        return webPort;
    }

    public static int getBroadcastIntervalMs() {
        return broadcastInterval;
    }

    public static int getMaxLogLines() {
        return maxLogLines;
    }

    public static String getMinLogLevel() {
        return minLogLevel;
    }

    public static String getSetupToken() {
        return setupToken;
    }

    public static void setSetupToken(String token) {
        synchronized (LOCK) {
            setupToken = token != null ? token.trim() : "";
            save();
        }
    }

    public static void reload() {
        synchronized (LOCK) {
            if (configPath != null && Files.exists(configPath)) {
                loadFrom(configPath);
            }
        }
    }

    private static void loadFrom(Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            Map<String, String> kv = new LinkedHashMap<String, String>();
            for (String raw : lines) {
                String line = stripComment(raw).trim();
                if (line.isEmpty() || line.startsWith("[")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq).trim();
                String v = line.substring(eq + 1).trim();
                kv.put(k, unquote(v));
            }

            webPort = clampInt(parseInt(kv.get("webPort"), 55533), 1024, 65535);
            broadcastInterval = clampInt(parseInt(kv.get("broadcastInterval"), 3000), 200, 60000);
            maxLogLines = clampInt(parseInt(kv.get("maxLogLines"), 1000), 100, 100000);

            String lvl = kv.get("minLogLevel");
            if (lvl != null && !lvl.trim().isEmpty()) {
                minLogLevel = lvl.trim();
            }
            String token = kv.get("setupToken");
            setupToken = token != null ? token.trim() : "";
        } catch (Exception e) {
            PanelLog.get().warn("Failed to read {}: {}", path, e.getMessage());
        }
    }

    private static void save() {
        if (configPath == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("# Utopia Server Panel configuration\n\n");
        sb.append("# The port for the web panel server (HTTP + WebSocket) [1024..65535]\n");
        sb.append("webPort = ").append(webPort).append("\n\n");
        sb.append("# Interval in milliseconds for broadcasting server status [200..60000]\n");
        sb.append("broadcastInterval = ").append(broadcastInterval).append("\n\n");
        sb.append("# Maximum number of log lines to keep in the console buffer [100..100000]\n");
        sb.append("maxLogLines = ").append(maxLogLines).append("\n\n");
        sb.append("# Minimum log level to capture (OFF, FATAL, ERROR, WARN, INFO, DEBUG, TRACE)\n");
        sb.append("minLogLevel = ").append(quote(minLogLevel)).append("\n\n");
        sb.append("# One-time token for initial web panel setup. Cleared automatically after setup.\n");
        sb.append("setupToken = ").append(quote(setupToken)).append("\n");

        try {
            Files.write(configPath, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            PanelLog.get().warn("Failed to write {}: {}", configPath, e.getMessage());
        }
    }

    private static String stripComment(String line) {
        boolean inQuotes = false;
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inQuotes) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (c == '#' && !inQuotes) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String unquote(String raw) {
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return raw;
    }

    private static String quote(String s) {
        String safe = s != null ? s : "";
        return "\"" + safe.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}

