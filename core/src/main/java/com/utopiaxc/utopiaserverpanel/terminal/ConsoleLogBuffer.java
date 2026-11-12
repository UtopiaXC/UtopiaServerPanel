package com.utopiaxc.utopiaserverpanel.terminal;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.config.PanelConfig;
import com.utopiaxc.utopiaserverpanel.web.controller.WebSocketController;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure Java 8 console log ring buffer and formatter decoupled from any logging framework.
 * Platform modules register thin Log4j/JUL bridges that call {@link #appendServerLog}.
 */
public final class ConsoleLogBuffer {
    private static final Gson GSON = new Gson();
    private static final LinkedList<String> LOGS = new LinkedList<String>();

    private static final Map<String, Integer> LEVEL_RANK = new HashMap<String, Integer>();
    static {
        LEVEL_RANK.put("OFF", 0);
        LEVEL_RANK.put("FATAL", 100);
        LEVEL_RANK.put("ERROR", 200);
        LEVEL_RANK.put("SEVERE", 200);
        LEVEL_RANK.put("WARN", 300);
        LEVEL_RANK.put("WARNING", 300);
        LEVEL_RANK.put("INFO", 400);
        LEVEL_RANK.put("DEBUG", 500);
        LEVEL_RANK.put("FINE", 500);
        LEVEL_RANK.put("TRACE", 600);
        LEVEL_RANK.put("FINEST", 600);
        LEVEL_RANK.put("ALL", Integer.MAX_VALUE);
    }

    private ConsoleLogBuffer() {}

    public static boolean shouldCapture(String levelName) {
        String thresholdStr = PanelConfig.getMinLogLevel();
        int threshold = rankOf(thresholdStr != null ? thresholdStr : "INFO", 400);
        if (threshold == 0) {
            return false;
        }
        int eventRank = rankOf(levelName, 400);
        return eventRank <= threshold;
    }

    private static int rankOf(String levelName, int fallback) {
        if (levelName == null) return fallback;
        Integer r = LEVEL_RANK.get(levelName.trim().toUpperCase(Locale.ROOT));
        return r != null ? r : fallback;
    }

    public static void appendServerLog(long timeMillis, String levelName, String loggerName,
                                       String threadName, String message, Throwable thrown) {
        if (!shouldCapture(levelName)) {
            return;
        }

        JsonObject entry = new JsonObject();
        entry.addProperty("time", new SimpleDateFormat("HH:mm:ss").format(new Date(timeMillis)));
        entry.addProperty("timestamp", timeMillis);
        entry.addProperty("level", levelName != null ? levelName : "INFO");
        entry.addProperty("logger", loggerName != null ? loggerName : "");
        entry.addProperty("thread", threadName != null ? threadName : "");
        entry.addProperty("source", "server");
        entry.addProperty("message", message != null ? message : "");

        if (thrown != null) {
            StringBuilder sb = new StringBuilder();
            if (thrown.toString() != null) {
                sb.append(thrown.toString()).append('\n');
            }
            for (StackTraceElement ste : thrown.getStackTrace()) {
                sb.append(ste.toString()).append('\n');
            }
            entry.addProperty("exception", sb.toString());
        }

        appendAndBroadcast(GSON.toJson(entry));
    }

    public static void addWebLog(String command) {
        long now = System.currentTimeMillis();
        JsonObject entry = new JsonObject();
        entry.addProperty("time", new SimpleDateFormat("HH:mm:ss").format(new Date(now)));
        entry.addProperty("timestamp", now);
        entry.addProperty("level", "WEB");
        entry.addProperty("logger", "WebConsole");
        entry.addProperty("thread", "Web Panel");
        entry.addProperty("message", command != null ? command : "");
        entry.addProperty("source", "web");

        appendAndBroadcast(GSON.toJson(entry));
    }

    public static void addCommandOutput(String level, String logger, String message) {
        long now = System.currentTimeMillis();
        JsonObject entry = new JsonObject();
        entry.addProperty("time", new SimpleDateFormat("HH:mm:ss").format(new Date(now)));
        entry.addProperty("timestamp", now);
        entry.addProperty("level", level != null ? level : "INFO");
        entry.addProperty("logger", logger != null ? logger : "Command");
        entry.addProperty("thread", "Server");
        entry.addProperty("message", message != null ? message : "");
        entry.addProperty("source", "server");
        entry.addProperty("messageFormat", "html");

        appendAndBroadcast(GSON.toJson(entry));
    }

    private static void appendAndBroadcast(String json) {
        int maxLines = Math.max(100, PanelConfig.getMaxLogLines());
        synchronized (LOGS) {
            LOGS.add(json);
            while (LOGS.size() > maxLines) {
                LOGS.removeFirst();
            }
        }

        try {
            WebSocketController.broadcastLog(json);
        } catch (Exception ignored) {}
    }

    public static List<String> getLogs() {
        synchronized (LOGS) {
            return new LinkedList<String>(LOGS);
        }
    }

    public static void clearLogs() {
        synchronized (LOGS) {
            LOGS.clear();
        }
    }
}

