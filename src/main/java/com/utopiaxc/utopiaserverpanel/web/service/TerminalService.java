package com.utopiaxc.utopiaserverpanel.web.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.utopiaxc.utopiaserverpanel.terminal.TerminalCapture;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class TerminalService {
    private static final Gson GSON = new Gson();

    public static String getLogsJson() {
        List<String> logs = TerminalCapture.getLogs();
        JsonArray jsonArray = new JsonArray();
        for (String log : logs) {
            try {
                jsonArray.add(GSON.fromJson(log, JsonObject.class));
            } catch (Exception e) {
                jsonArray.add(log);
            }
        }
        JsonObject wsMsg = new JsonObject();
        wsMsg.addProperty("type", "logs");
        wsMsg.add("data", jsonArray);
        return GSON.toJson(wsMsg);
    }

    public static void executeCommand(MinecraftServer minecraftServer, String command) {
        if (command.trim().isEmpty()) return;
        String cmd = command.trim();
        minecraftServer.execute(() -> {
            minecraftServer.getCommands().performPrefixedCommand(
                    minecraftServer.createCommandSourceStack(), cmd);
        });
    }

    public static String getCompletionsJson(MinecraftServer minecraftServer, String command, String requestId) {
        JsonArray completionsArray = new JsonArray();
        if (!command.isEmpty()) {
            try {
                var dispatcher = minecraftServer.getCommands().getDispatcher();
                var parseResults = dispatcher.parse(command, minecraftServer.createCommandSourceStack());
                Suggestions suggestions = dispatcher.getCompletionSuggestions(parseResults).get();
                for (Suggestion suggestion : suggestions.getList()) {
                    completionsArray.add(suggestion.getText());
                }
            } catch (InterruptedException | ExecutionException e) {
            }
        }
        JsonObject wsMsg = new JsonObject();
        wsMsg.addProperty("type", "completions");
        wsMsg.addProperty("requestId", requestId);
        wsMsg.add("data", completionsArray);
        return GSON.toJson(wsMsg);
    }

    public static JsonArray getWhitelistedPlayers(MinecraftServer minecraftServer) {
        JsonArray players = new JsonArray();
        try {
            java.nio.file.Path whitelistFile = minecraftServer.getServerDirectory().resolve("whitelist.json");
            if (java.nio.file.Files.exists(whitelistFile)) {
                String content = java.nio.file.Files.readString(whitelistFile);
                JsonArray arr = GSON.fromJson(content, JsonArray.class);
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject obj = arr.get(i).getAsJsonObject();
                    if (obj.has("name")) {
                        players.add(obj.get("name").getAsString());
                    }
                }
            }
        } catch (Exception e) {
        }
        return players;
    }
}
