package com.utopiaxc.utopiaserverpanel.web.context;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Shared JSON request parsing utilities for HTTP controllers.
 */
public final class JsonHelper {
    private static final Gson GSON = new Gson();

    private JsonHelper() {}

    public static JsonObject parseObject(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new JsonObject();
        JsonObject obj = GSON.fromJson(raw, JsonObject.class);
        return obj != null ? obj : new JsonObject();
    }

    public static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        return obj.get(key).getAsString();
    }

    public static Integer intObj(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        JsonElement el = obj.get(key);
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() && el.getAsString().trim().isEmpty()) return null;
        return Integer.valueOf(el.getAsInt());
    }

    public static int intVal(JsonObject obj, String key, int fallback) {
        Integer v = intObj(obj, key);
        return v != null ? v : fallback;
    }

    public static Boolean boolObj(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        return obj.get(key).getAsBoolean();
    }

    public static boolean boolVal(JsonObject obj, String key, boolean fallback) {
        Boolean v = boolObj(obj, key);
        return v != null ? v : fallback;
    }
}

