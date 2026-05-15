package com.utopiaxc.utopiaserverpanel.terminal;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.Config;
import com.utopiaxc.utopiaserverpanel.web.controller.WebSocketController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.util.LinkedList;
import java.util.List;

/**
 * Log4j2 appender that captures server log output for the web panel.
 * Maintains a rolling buffer of recent log lines and broadcasts
 * new lines to connected WebSocket clients in real-time as structured JSON.
 */
public class TerminalCapture extends AbstractAppender {
    private static final Gson GSON = new Gson();
    private static final LinkedList<String> logs = new LinkedList<>();
    private static final PatternLayout LAYOUT = PatternLayout.newBuilder()
            .withPattern("[%d{HH:mm:ss}] [%t/%p] [%c]: %m%n")
            .withNoConsoleNoAnsi(false)
            .withAlwaysWriteExceptions(true)
            .build();

    protected TerminalCapture(String name, Filter filter) {
        super(name, filter, LAYOUT, true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
        // Filter out sensitive Netty debug logs
        if ("io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker".equals(event.getLoggerName())) {
            // These might contain handshake keys which users consider sensitive
            if (event.getLevel() == org.apache.logging.log4j.Level.DEBUG) {
                return;
            }
        }

        // Build structured JSON log entry for the frontend
        JsonObject entry = new JsonObject();
        entry.addProperty("time", new java.text.SimpleDateFormat("HH:mm:ss")
                .format(new java.util.Date(event.getTimeMillis())));
        entry.addProperty("level", event.getLevel().name());
        entry.addProperty("logger", event.getLoggerName());
        entry.addProperty("thread", event.getThreadName());
        entry.addProperty("source", "server");

        // Raw message with ANSI preserved (for Spark compatibility)
        String formatted = event.getMessage().getFormattedMessage();
        entry.addProperty("message", formatted != null ? formatted : "");

        // Exception details
        if (event.getThrown() != null) {
            StringBuilder sb = new StringBuilder();
            for (StackTraceElement ste : event.getThrown().getStackTrace()) {
                sb.append(ste.toString()).append("\n");
            }
            entry.addProperty("exception", sb.toString());
        }

        String json = GSON.toJson(entry);

        synchronized (logs) {
            logs.add(json);
            int maxLines = Config.MAX_LOG_LINES.getAsInt();
            while (logs.size() > maxLines) {
                logs.removeFirst();
            }
        }

        // Broadcast to all connected WebSocket clients in real-time
        try {
            WebSocketController.broadcastLog(json);
        } catch (Exception e) {
            // Ignore if websocket controller isn't ready
        }
    }

    public static List<String> getLogs() {
        synchronized (logs) {
            return new LinkedList<>(logs);
        }
    }

    public static void clearLogs() {
        synchronized (logs) {
            logs.clear();
        }
    }

    /** Inject a log line from the web console (player-issued command). */
    public static void addWebLog(String command) {
        JsonObject entry = new JsonObject();
        entry.addProperty("time", new java.text.SimpleDateFormat("HH:mm:ss")
                .format(new java.util.Date()));
        entry.addProperty("level", "WEB");
        entry.addProperty("logger", "WebConsole");
        entry.addProperty("thread", "Web Panel");
        entry.addProperty("message", command);
        entry.addProperty("source", "web");

        String json = GSON.toJson(entry);
        synchronized (logs) {
            logs.add(json);
            int maxLines = Config.MAX_LOG_LINES.getAsInt();
            while (logs.size() > maxLines) {
                logs.removeFirst();
            }
        }

        try {
            WebSocketController.broadcastLog(json);
        } catch (Exception ignored) {
        }
    }

    public static void register() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        TerminalCapture appender = new TerminalCapture("TerminalCaptureAppender", null);
        appender.start();
        ctx.getConfiguration().addAppender(appender);
        ctx.getRootLogger().addAppender(ctx.getConfiguration().getAppender(appender.getName()));
        ctx.updateLoggers();
    }
}
