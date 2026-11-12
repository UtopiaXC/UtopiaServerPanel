package com.utopiaxc.utopiaserverpanel.web.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.cache.ServerStatusCache;

/**
 * Service for collecting and serializing Minecraft server status data.
 * Now reads from the {@link ServerStatusCache} instead of querying the MC server directly.
 */
public class StatusService {
    private static final Gson GSON = new Gson();

    /** Build the status data as a JsonObject from cache. */
    public static JsonObject getStatusObject() {
        return ServerStatusCache.getInstance().getSnapshot();
    }

    /** Wrap status data with WS message envelope {type, data} for WebSocket push. */
    public static String getStatusJson() {
        JsonObject status = getStatusObject();
        JsonObject wsMsg = new JsonObject();
        wsMsg.addProperty("type", "status");
        wsMsg.add("data", status);
        return GSON.toJson(wsMsg);
    }
}