package com.utopiaxc.utopiaserverpanel.web.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;
import com.utopiaxc.utopiaserverpanel.adapter.util.ServerPropertiesReader;
import com.utopiaxc.utopiaserverpanel.terminal.ConsoleLogBuffer;

import java.util.List;
import java.util.LinkedList;

/**
 * Service for terminal/console operations.
 * Delegates game-specific calls to the adapter layer.
 */
public class TerminalService {
    private static final Gson GSON = new Gson();
    private static final LinkedList<String> commandHistory = new LinkedList<String>();
    private static final int MAX_HISTORY = 100;

    public static String getLogsJson() {
        List<String> logs = ConsoleLogBuffer.getLogs();
        JsonArray jsonArray = new JsonArray();
        for (String log : logs) {
            try {
                jsonArray.add(GSON.fromJson(log, JsonObject.class));
            } catch (Exception e) {
                jsonArray.add(new JsonPrimitive(log));
            }
        }
        JsonObject wsMsg = new JsonObject();
        wsMsg.addProperty("type", "logs");
        wsMsg.add("data", jsonArray);
        return GSON.toJson(wsMsg);
    }

    public static void executeCommand(String command) {
        if (command == null || command.trim().isEmpty()) return;
        String cmd = command.trim();
        // Store in history
        synchronized (commandHistory) {
            if (commandHistory.isEmpty() || !commandHistory.getLast().equals(cmd)) {
                commandHistory.addLast(cmd);
                if (commandHistory.size() > MAX_HISTORY) commandHistory.removeFirst();
            }
        }
        AdapterRegistry.getAdapter().executeCommand(cmd);
    }

    public static String getCompletionsJson(String command, String requestId) {
        JsonArray completionsArray = new JsonArray();
        if (command != null && !command.isEmpty()) {
            List<String> items = AdapterRegistry.getAdapter().getCommandCompletions(command);
            if (items != null) {
                for (String item : items) {
                    completionsArray.add(new JsonPrimitive(item));
                }
            }
        }
        JsonObject wsMsg = new JsonObject();
        wsMsg.addProperty("type", "completions");
        wsMsg.addProperty("requestId", requestId);
        wsMsg.add("data", completionsArray);
        return GSON.toJson(wsMsg);
    }

    public static JsonArray getWhitelistedPlayers() {
        JsonArray arr = new JsonArray();
        List<String> names = ServerPropertiesReader.readWhitelistPlayers(
                AdapterRegistry.getAdapter().getServerDirectory());
        for (String name : names) {
            arr.add(new JsonPrimitive(name));
        }
        return arr;
    }

    /** Return command history as a JSON WebSocket message. */
    public static String getCommandHistoryJson() {
        JsonArray arr = new JsonArray();
        synchronized (commandHistory) {
            for (String cmd : commandHistory) {
                arr.add(new JsonPrimitive(cmd));
            }
        }
        JsonObject wsMsg = new JsonObject();
        wsMsg.addProperty("type", "command_history");
        wsMsg.add("data", arr);
        return GSON.toJson(wsMsg);
    }
}
