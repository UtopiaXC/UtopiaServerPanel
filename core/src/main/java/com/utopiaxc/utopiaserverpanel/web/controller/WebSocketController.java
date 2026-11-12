package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
import com.utopiaxc.utopiaserverpanel.terminal.ConsoleLogBuffer;
import com.utopiaxc.utopiaserverpanel.web.cache.PlayerDataCache;
import com.utopiaxc.utopiaserverpanel.web.handler.WebSocketFrameHandler;
import com.utopiaxc.utopiaserverpanel.web.middleware.TokenAuthHelper;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionService;
import com.utopiaxc.utopiaserverpanel.web.service.StatusService;
import com.utopiaxc.utopiaserverpanel.web.service.TerminalService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.Map;

/**
 * Permission-aware WebSocket controller supporting both JWT and Personal Access Tokens.
 */
public final class WebSocketController {
    private static final Gson GSON = new Gson();
    private static final Object STATUS_LOCK = new Object();
    private static JsonObject lastBroadcastedStatus = null;

    private WebSocketController() {}

    public static void reset() {
        synchronized (STATUS_LOCK) {
            lastBroadcastedStatus = null;
        }
    }

    private static JsonObject computeDelta(JsonObject oldObj, JsonObject newObj) {
        if (oldObj == null) return newObj;
        JsonObject delta = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : newObj.entrySet()) {
            String key = entry.getKey();
            JsonElement newVal = entry.getValue();
            if (newVal.isJsonObject() && oldObj.has(key) && oldObj.get(key).isJsonObject()) {
                JsonObject subDelta = computeDelta(oldObj.getAsJsonObject(key), newVal.getAsJsonObject());
                if (subDelta.entrySet().size() > 0) delta.add(key, subDelta);
            } else if (!oldObj.has(key) || !newVal.equals(oldObj.get(key))) {
                delta.add(key, newVal);
            }
        }
        return delta;
    }

    private static boolean hasChannelPermission(Channel ch, String perm) {
        Integer uid = ch.attr(WebSocketFrameHandler.USER_ID_ATTR).get();
        return uid == null
                ? PermissionService.guestMeetsRequirement(perm)
                : PermissionService.meetsRequirement(uid.intValue(), perm);
    }

    public static void onConnect(ChannelHandlerContext ctx) {
        PlayerDataCache.getInstance().addRef();
        Channel ch = ctx.channel();
        if (hasChannelPermission(ch, "terminal:1")) {
            ch.writeAndFlush(new TextWebSocketFrame(TerminalService.getLogsJson()));
        }
        if (hasChannelPermission(ch, "dashboard:1")) {
            JsonObject snap = StatusService.getStatusObject();
            if (snap != null && snap.entrySet().size() > 0) {
                ch.writeAndFlush(new TextWebSocketFrame(StatusService.getStatusJson()));
            }
        }
    }

    public static void onDisconnect(ChannelHandlerContext ctx) {
        PlayerDataCache.getInstance().removeRef();
    }

    public static void onMessage(ChannelHandlerContext ctx, String message) {
        try {
            JsonObject req = GSON.fromJson(message, JsonObject.class);
            if (req == null || !req.has("action")) return;

            String action = req.get("action").getAsString();
            Channel ch = ctx.channel();

            if (req.has("token")) {
                if (req.get("token").isJsonNull() || req.get("token").getAsString().trim().isEmpty()) {
                    ch.attr(WebSocketFrameHandler.USER_ID_ATTR).set(null);
                } else {
                    TokenAuthHelper.Principal principal = TokenAuthHelper.resolve(req.get("token").getAsString());
                    ch.attr(WebSocketFrameHandler.USER_ID_ATTR).set(principal != null ? Integer.valueOf(principal.getUserId()) : null);
                }
            }

            if ("auth".equals(action)) {
                if (hasChannelPermission(ch, "dashboard:1")) {
                    JsonObject snap = StatusService.getStatusObject();
                    if (snap != null && snap.entrySet().size() > 0) {
                        ch.writeAndFlush(new TextWebSocketFrame(StatusService.getStatusJson()));
                    }
                }
                if (hasChannelPermission(ch, "terminal:1")) {
                    ch.writeAndFlush(new TextWebSocketFrame(TerminalService.getLogsJson()));
                }
                return;
            }

            String reqPerm = null;
            if ("fetch_status".equals(action)) {
                reqPerm = "dashboard:1";
            } else if ("fetch_logs".equals(action) || "fetch_command_history".equals(action) || "fetch_completions".equals(action)) {
                reqPerm = "terminal:1";
            } else if ("execute_command".equals(action)) {
                reqPerm = "terminal:2";
            }

            if (reqPerm != null && !hasChannelPermission(ch, reqPerm)) {
                return;
            }

            switch (action) {
                case "fetch_logs":
                    ch.writeAndFlush(new TextWebSocketFrame(TerminalService.getLogsJson()));
                    break;
                case "fetch_status": {
                    JsonObject snap = StatusService.getStatusObject();
                    if (snap != null && snap.entrySet().size() > 0) {
                        ch.writeAndFlush(new TextWebSocketFrame(StatusService.getStatusJson()));
                    }
                    break;
                }
                case "execute_command":
                    if (req.has("command") && !req.get("command").isJsonNull()) {
                        String cmd = req.get("command").getAsString();
                        TerminalService.executeCommand(cmd);
                        ConsoleLogBuffer.addWebLog("> " + cmd);
                    }
                    break;
                case "fetch_completions":
                    if (req.has("command") && !req.get("command").isJsonNull()) {
                        String requestId = req.has("requestId") && !req.get("requestId").isJsonNull()
                                ? req.get("requestId").getAsString() : "";
                        String completionsJson = TerminalService.getCompletionsJson(
                                req.get("command").getAsString(), requestId);
                        ch.writeAndFlush(new TextWebSocketFrame(completionsJson));
                    }
                    break;
                case "fetch_command_history":
                    ch.writeAndFlush(new TextWebSocketFrame(TerminalService.getCommandHistoryJson()));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            PanelLog.get().error("WebSocket message handling error", e);
        }
    }

    public static void broadcastLog(String logJson) {
        ChannelGroup channels = WebSocketFrameHandler.getChannels();
        if (channels.isEmpty()) return;

        JsonObject wsMsg = new JsonObject();
        wsMsg.addProperty("type", "new_log");
        wsMsg.addProperty("data_json", logJson);
        String payload = GSON.toJson(wsMsg);

        for (Channel ch : channels) {
            if (hasChannelPermission(ch, "terminal:1")) {
                ch.writeAndFlush(new TextWebSocketFrame(payload));
            }
        }
    }

    public static void broadcastStatus() {
        ChannelGroup channels = WebSocketFrameHandler.getChannels();
        if (channels.isEmpty()) return;

        JsonObject currentStatus = StatusService.getStatusObject();
        if (currentStatus == null || currentStatus.entrySet().size() == 0) return;

        JsonObject delta;
        synchronized (STATUS_LOCK) {
            delta = computeDelta(lastBroadcastedStatus, currentStatus);
            lastBroadcastedStatus = currentStatus;
        }

        JsonObject wsMsg = new JsonObject();
        wsMsg.addProperty("type", "status_delta");
        wsMsg.addProperty("timestamp", System.currentTimeMillis());
        wsMsg.add("data", delta);
        String payload = GSON.toJson(wsMsg);

        for (Channel ch : channels) {
            if (hasChannelPermission(ch, "dashboard:1")) {
                ch.writeAndFlush(new TextWebSocketFrame(payload));
            }
        }
    }

    public static void broadcastMonitorLog(Map<String, Object> log) {
        ChannelGroup channels = WebSocketFrameHandler.getChannels();
        if (channels.isEmpty()) return;

        JsonObject wsMsg = new JsonObject();
        wsMsg.addProperty("type", "new_monitor_log");
        wsMsg.add("data", GSON.toJsonTree(log));
        String payload = GSON.toJson(wsMsg);

        for (Channel ch : channels) {
            if (hasChannelPermission(ch, "logs:1")) {
                ch.writeAndFlush(new TextWebSocketFrame(payload));
            }
        }
    }

    public static void broadcastPlayerEvent(Map<String, Object> event) {
        ChannelGroup channels = WebSocketFrameHandler.getChannels();
        if (channels.isEmpty()) return;

        JsonObject wsMsg = new JsonObject();
        wsMsg.addProperty("type", "new_player_event");
        wsMsg.add("data", GSON.toJsonTree(event));
        String payload = GSON.toJson(wsMsg);

        for (Channel ch : channels) {
            if (hasChannelPermission(ch, "logs:1")) {
                ch.writeAndFlush(new TextWebSocketFrame(payload));
            }
        }
    }
}
