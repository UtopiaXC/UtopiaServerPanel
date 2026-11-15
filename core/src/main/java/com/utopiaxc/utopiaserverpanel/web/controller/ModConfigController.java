package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.ModConfigService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for Mods List, Mod Config File Editor, {@code server.properties} Editor, and Restart.
 */
public final class ModConfigController {

    private ModConfigController() {}

    /** GET /api/mods/overview */
    public static void getOverview(RequestContext ctx) {
        ResponseHelper.sendOk(ctx, ModConfigService.getOverview());
    }

    /** GET /api/mods/config?path=... */
    public static void readConfig(RequestContext ctx) {
        String relPath = ctx.queryParam("path");
        try {
            ResponseHelper.sendOk(ctx, ModConfigService.readConfigFile(relPath));
        } catch (IllegalArgumentException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_CONFIG_PATH", e.getMessage());
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "CONFIG_READ_ERROR", e.getMessage());
        }
    }

    /** PUT /api/mods/config */
    public static void saveConfig(RequestContext ctx) {
        JsonObject body = JsonHelper.parseObject(ctx.body());
        String relPath = JsonHelper.str(body, "path");
        String content = JsonHelper.str(body, "content");
        List<Map<String, String>> updates = extractUpdates(body);
        try {
            ModConfigService.saveConfigFile(relPath, content, updates);
            Map<String, Object> res = new LinkedHashMap<String, Object>();
            res.put("saved", Boolean.TRUE);
            res.put("pendingRestart", Boolean.valueOf(ModConfigService.isPendingRestart()));
            ResponseHelper.sendOk(ctx, res);
        } catch (IllegalArgumentException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_CONFIG", e.getMessage());
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "CONFIG_SAVE_ERROR", e.getMessage());
        }
    }

    /** GET /api/mods/server-properties */
    public static void getServerProperties(RequestContext ctx) {
        try {
            ResponseHelper.sendOk(ctx, ModConfigService.getServerPropertiesView());
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "PROPS_READ_ERROR", e.getMessage());
        }
    }

    /** PUT /api/mods/server-properties */
    public static void saveServerProperties(RequestContext ctx) {
        JsonObject body = JsonHelper.parseObject(ctx.body());
        String raw = JsonHelper.str(body, "raw");
        List<Map<String, String>> updates = extractUpdates(body);
        try {
            ModConfigService.saveServerProperties(raw, updates);
            Map<String, Object> res = new LinkedHashMap<String, Object>();
            res.put("saved", Boolean.TRUE);
            res.put("pendingRestart", Boolean.valueOf(ModConfigService.isPendingRestart()));
            ResponseHelper.sendOk(ctx, res);
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "PROPS_SAVE_ERROR", e.getMessage());
        }
    }

    /** POST /api/mods/restart */
    public static void triggerRestart(RequestContext ctx) {
        String admin = (String) ctx.getAttribute("username");
        ResponseHelper.sendOk(ctx, ModConfigService.triggerRestart(admin != null ? admin : "admin"));
    }

    private static List<Map<String, String>> extractUpdates(JsonObject body) {
        if (!body.has("updates") || !body.get("updates").isJsonArray()) return null;
        JsonArray arr = body.getAsJsonArray("updates");
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement el = arr.get(i);
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            Map<String, String> row = new LinkedHashMap<String, String>();
            if (o.has("section") && !o.get("section").isJsonNull()) row.put("section", o.get("section").getAsString());
            if (o.has("key") && !o.get("key").isJsonNull()) row.put("key", o.get("key").getAsString());
            if (o.has("value") && !o.get("value").isJsonNull()) row.put("value", o.get("value").getAsString());
            if (o.has("type") && !o.get("type").isJsonNull()) row.put("type", o.get("type").getAsString());
            list.add(row);
        }
        return list;
    }
}
