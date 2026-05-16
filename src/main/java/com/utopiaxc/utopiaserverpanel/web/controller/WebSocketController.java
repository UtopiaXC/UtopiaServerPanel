package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import com.utopiaxc.utopiaserverpanel.terminal.TerminalCapture;
import com.utopiaxc.utopiaserverpanel.web.WebServer;
import com.utopiaxc.utopiaserverpanel.web.handler.WebSocketFrameHandler;
import com.utopiaxc.utopiaserverpanel.web.service.StatusService;
import com.utopiaxc.utopiaserverpanel.web.service.TerminalService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * WebSocket message controller — handles action-based routing.
 */
public final class WebSocketController {
    private static final Gson GSON = new Gson();

    private static JsonObject lastBroadcastedStatus = null;

    private WebSocketController() {}

    private static JsonObject computeDelta(JsonObject oldObj, JsonObject newObj) {
        if (oldObj == null) return newObj;
        JsonObject delta = new JsonObject();
        for (String key : newObj.keySet()) {
            if (newObj.get(key).isJsonObject() && oldObj.has(key) && oldObj.get(key).isJsonObject()) {
                JsonObject subDelta = computeDelta(oldObj.getAsJsonObject(key), newObj.getAsJsonObject(key));
                if (subDelta.size() > 0) delta.add(key, subDelta);
            } else if (!oldObj.has(key) || !newObj.get(key).equals(oldObj.get(key))) {
                delta.add(key, newObj.get(key));
            }
        }
        return delta;
    }

    public static void onConnect(ChannelHandlerContext ctx) {
        String logsJson = TerminalService.getLogsJson();
        ctx.channel().writeAndFlush(new TextWebSocketFrame(logsJson));
    }

    public static void onMessage(ChannelHandlerContext ctx, String message) {
        try {
            JsonObject req = GSON.fromJson(message, JsonObject.class);
            if (!req.has("action")) return;

            String action = req.get("action").getAsString();

            switch (action) {
                case "fetch_logs" -> {
                    String logsJson = TerminalService.getLogsJson();
                    ctx.channel().writeAndFlush(new TextWebSocketFrame(logsJson));
                }
                case "fetch_status" -> {
                    String statusJson = StatusService.getStatusJson(
                            WebServer.getMinecraftServer(), WebServer.getStartTime());
                    ctx.channel().writeAndFlush(new TextWebSocketFrame(statusJson));
                }
                case "execute_command" -> {
                    if (req.has("command")) {
                        String cmd = req.get("command").getAsString();
                        // Echo web-issued command to all terminals
                        TerminalService.executeCommand(WebServer.getMinecraftServer(), cmd);
                        TerminalCapture.addWebLog("> " + cmd);
                    }
                }
                case "fetch_completions" -> {
                    if (req.has("command")) {
                        String requestId = req.has("requestId") ? req.get("requestId").getAsString() : "";
                        String completionsJson = TerminalService.getCompletionsJson(
                                WebServer.getMinecraftServer(), req.get("command").getAsString(), requestId);
                        ctx.channel().writeAndFlush(new TextWebSocketFrame(completionsJson));
                    }
                }
                case "fetch_command_history" -> {
                    String historyJson = TerminalService.getCommandHistoryJson();
                    ctx.channel().writeAndFlush(new TextWebSocketFrame(historyJson));
                }
            }
        } catch (Exception e) {
            UtopiaServerPanel.LOGGER.error("WebSocket message handling error", e);
        }
    }

    public static void broadcastLog(String logJson) {
        var channels = WebSocketFrameHandler.getChannels();
        if (channels.isEmpty()) return;

        JsonObject wsMsg = new JsonObject();
        wsMsg.addProperty("type", "new_log");
        // data is already a JSON string (structured log entry)
        wsMsg.addProperty("data_json", logJson);
        String json = GSON.toJson(wsMsg);
        channels.writeAndFlush(new TextWebSocketFrame(json));
    }

    /** Broadcast server status to all connected WebSocket clients. */
    public static void broadcastStatus(net.minecraft.server.MinecraftServer minecraftServer, long startTime) {
        var channels = WebSocketFrameHandler.getChannels();
        if (channels.isEmpty()) return;

        JsonObject currentStatus = StatusService.getStatusObject(minecraftServer, startTime);
        JsonObject delta = computeDelta(lastBroadcastedStatus, currentStatus);
        
        lastBroadcastedStatus = currentStatus;
        JsonObject wsMsg = new JsonObject();
        wsMsg.addProperty("type", "status_delta");
        wsMsg.addProperty("timestamp", System.currentTimeMillis());
        wsMsg.add("data", delta);
        channels.writeAndFlush(new TextWebSocketFrame(GSON.toJson(wsMsg)));
    }
}
