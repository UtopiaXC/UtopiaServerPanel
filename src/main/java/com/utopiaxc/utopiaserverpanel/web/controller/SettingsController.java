package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.MonitorConfigService;
import com.utopiaxc.utopiaserverpanel.web.service.SiteConfigService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Map;

/**
 * HTTP controller for site and monitor settings endpoints.
 */
public final class SettingsController {
    private static final Gson GSON = new Gson();

    private SettingsController() {}

    // ────────────── Site Settings ──────────────

    /** GET /api/settings/site — public, returns site name */
    public static void getSiteName(RequestContext ctx) {
        String name = SiteConfigService.getSiteName();
        ResponseHelper.sendOk(ctx, Map.of("name", name));
    }

    /** PUT /api/settings/site — admin:2, update site name */
    public static void setSiteName(RequestContext ctx) {
        try {
            JsonObject body = GSON.fromJson(ctx.body(), JsonObject.class);
            String name = body.has("name") && !body.get("name").isJsonNull()
                    ? body.get("name").getAsString() : null;
            if (name == null || name.trim().isEmpty()) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Name is required");
                return;
            }
            SiteConfigService.setSiteName(name.trim());
            ResponseHelper.sendOk(ctx, Map.of("name", SiteConfigService.getSiteName()));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid request body");
        }
    }

    // ────────────── Monitor Settings ──────────────

    /** GET /api/settings/monitor — admin:1, returns monitor config */
    public static void getMonitorConfig(RequestContext ctx) {
        ResponseHelper.sendOk(ctx, Map.of(
                "enabled", MonitorConfigService.isEnabled(),
                "intervalSeconds", MonitorConfigService.getIntervalSeconds(),
                "retentionDays", MonitorConfigService.getRetentionDays(),
                "showPlayerNames", MonitorConfigService.isShowPlayerNames()
        ));
    }

    /** PUT /api/settings/monitor — admin:2, update monitor config */
    public static void setMonitorConfig(RequestContext ctx) {
        try {
            JsonObject body = GSON.fromJson(ctx.body(), JsonObject.class);

            boolean enabled = body.has("enabled") ? body.get("enabled").getAsBoolean()
                    : MonitorConfigService.isEnabled();
            int interval = body.has("intervalSeconds") ? body.get("intervalSeconds").getAsInt()
                    : MonitorConfigService.getIntervalSeconds();
            int retention = body.has("retentionDays") ? body.get("retentionDays").getAsInt()
                    : MonitorConfigService.getRetentionDays();
            boolean showNames = body.has("showPlayerNames") ? body.get("showPlayerNames").getAsBoolean()
                    : MonitorConfigService.isShowPlayerNames();

            MonitorConfigService.saveAll(enabled, interval, retention, showNames);

            ResponseHelper.sendOk(ctx, Map.of(
                    "enabled", MonitorConfigService.isEnabled(),
                    "intervalSeconds", MonitorConfigService.getIntervalSeconds(),
                    "retentionDays", MonitorConfigService.getRetentionDays(),
                    "showPlayerNames", MonitorConfigService.isShowPlayerNames()
            ));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid request body");
        }
    }
}
