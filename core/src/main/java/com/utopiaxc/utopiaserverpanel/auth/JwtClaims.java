package com.utopiaxc.utopiaserverpanel.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Lightweight immutable wrapper around verified JWT payload claims.
 */
public final class JwtClaims {
    private final JsonObject payload;

    public JwtClaims(JsonObject payload) {
        this.payload = payload != null ? payload : new JsonObject();
    }

    public String getSubject() {
        return getString("sub");
    }

    public String getString(String key) {
        if (!payload.has(key)) return null;
        JsonElement el = payload.get(key);
        if (el == null || el.isJsonNull()) return null;
        return el.getAsString();
    }

    public Integer getInteger(String key) {
        if (!payload.has(key)) return null;
        JsonElement el = payload.get(key);
        if (el == null || el.isJsonNull()) return null;
        try {
            return el.getAsInt();
        } catch (Exception e) {
            return null;
        }
    }

    public Long getLong(String key) {
        if (!payload.has(key)) return null;
        JsonElement el = payload.get(key);
        if (el == null || el.isJsonNull()) return null;
        try {
            return el.getAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    public Boolean getBoolean(String key) {
        if (!payload.has(key)) return null;
        JsonElement el = payload.get(key);
        if (el == null || el.isJsonNull()) return null;
        try {
            return el.getAsBoolean();
        } catch (Exception e) {
            return null;
        }
    }
}

