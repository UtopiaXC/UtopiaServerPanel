package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.mail.SmtpMailer;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.MonitorConfigService;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionService;
import com.utopiaxc.utopiaserverpanel.web.service.SettingsService;
import com.utopiaxc.utopiaserverpanel.web.service.WhitelistService;
import com.utopiaxc.utopiaserverpanel.web.service.WorldRegistryService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Administrative system configuration endpoints (/api/admin/settings).
 */
public final class AdminSettingsController {

    private AdminSettingsController() {}

    /** GET /api/admin/settings */
    public static void getSettings(RequestContext ctx) {
        ResponseHelper.sendOk(ctx, SettingsService.getAdminSettings());
    }

    /** PATCH /api/admin/settings */
    public static void updateSettings(RequestContext ctx) {
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            Map<String, String> updates = new LinkedHashMap<>();

            putStrIfPresent(body, "siteName", "site_name", updates);
            putStrIfPresent(body, "siteDescription", "site_description", updates);
            putStrIfPresent(body, "baseUrl", "base_url", updates);
            putStrIfPresent(body, "defaultLocale", "default_locale", updates);

            putBoolIfPresent(body, "allowRegistration", "allow_registration", updates);
            putIntIfPresent(body, "defaultRegisterRoleId", "default_register_role_id", updates, 1, 999999);
            putBoolIfPresent(body, "requireEmailVerification", "require_email_verification", updates);
            putBoolIfPresent(body, "requireBindingOnRegister", "require_binding_on_register", updates);
            putBoolIfPresent(body, "forcePlayerBinding", "force_player_binding", updates);

            putStrIfPresent(body, "captchaPolicy", "captcha_policy", updates);
            putIntIfPresent(body, "captchaDifficulty", "captcha_difficulty", updates, 10_000, 1_000_000);
            putIntIfPresent(body, "sessionTimeoutDays", "session_timeout_days", updates, 1, 365);
            putBoolIfPresent(body, "force2fa", "force_2fa", updates);
            putIntIfPresent(body, "maxFailedLogins", "max_failed_logins", updates, 3, 50);
            putIntIfPresent(body, "lockoutMinutes", "lockout_minutes", updates, 1, 1440);

            putBoolIfPresent(body, "smtpEnabled", "smtp_enabled", updates);
            putStrIfPresent(body, "smtpHost", "smtp_host", updates);
            putIntIfPresent(body, "smtpPort", "smtp_port", updates, 1, 65535);
            putStrIfPresent(body, "smtpEncryption", "smtp_encryption", updates);
            putStrIfPresent(body, "smtpUser", "smtp_user", updates);
            putStrIfPresent(body, "smtpFrom", "smtp_from", updates);
            putStrIfPresent(body, "smtpFromName", "smtp_from_name", updates);

            // Map settings
            putBoolIfPresent(body, "mapEnabled", "map_enabled", updates);
            putStrIfPresent(body, "mapRenderMode", "map_render_mode", updates);
            putIntIfPresent(body, "mapMaxChunksPerTick", "map_max_chunks_per_tick", updates, 1, 16);
            putIntIfPresent(body, "mapCacheMaxMb", "map_cache_max_mb", updates, 32, 16384);
            putVisIfPresent(body, "mapVisTerrain", "map_vis_terrain", updates);
            putVisIfPresent(body, "mapVisNetherCeiling", "map_vis_nether_ceiling", updates);
            putVisIfPresent(body, "mapVisGrid", "map_vis_grid", updates);
            putVisIfPresent(body, "mapVisCoordinates", "map_vis_coordinates", updates);
            putVisIfPresent(body, "mapVisWaterDepth", "map_vis_water_depth", updates);
            putVisIfPresent(body, "mapVisHeightShading", "map_vis_height_shading", updates);
            putVisIfPresent(body, "mapVisBiomeTint", "map_vis_biome_tint", updates);
            putVisIfPresent(body, "mapVisPlayers", "map_vis_players", updates);
            putVisIfPresent(body, "mapVisWaypoints", "map_vis_waypoints", updates);
            putVisIfPresent(body, "mapVisMobsHostile", "map_vis_mobs_hostile", updates);
            putVisIfPresent(body, "mapVisMobsPassive", "map_vis_mobs_passive", updates);
            putVisIfPresent(body, "mapVisEntities", "map_vis_entities", updates);
            putVisIfPresent(body, "mapVisStructures", "map_vis_structures", updates);
            putVisIfPresent(body, "mapVisTeleport", "map_vis_teleport", updates);

            // Whitelist & lifecycle & chat
            Boolean reqServerWhitelist = JsonHelper.boolObj(body, "serverWhitelistEnabled");
            Boolean reqWhitelistMgmt = JsonHelper.boolObj(body, "whitelistManagementEnabled");
            if (reqServerWhitelist != null) {
                WhitelistService.setServerWhitelistEnabled(reqServerWhitelist.booleanValue());
            } else if (Boolean.TRUE.equals(reqWhitelistMgmt) && !SettingsService.isServerWhitelistEnabled()) {
                WhitelistService.setServerWhitelistEnabled(true);
            }
            if (reqServerWhitelist != null && !reqServerWhitelist.booleanValue()) {
                updates.put("whitelist_management_enabled", "false");
            } else {
                putBoolIfPresent(body, "whitelistManagementEnabled", "whitelist_management_enabled", updates);
            }
            putIntIfPresent(body, "whitelistApplyCooldownSec", "whitelist_apply_cooldown_sec", updates, 0, 86400);
            putIntIfPresent(body, "whitelistMaxPendingPerIp", "whitelist_max_pending_per_ip", updates, 1, 50);
            putBoolIfPresent(body, "whitelistSendEmail", "whitelist_send_email", updates);
            putBoolIfPresent(body, "whitelistSocialRequired", "whitelist_social_required", updates);
            if (body.has("whitelistRequiredSocialPlatforms") && body.get("whitelistRequiredSocialPlatforms").isJsonArray()) {
                JsonArray arr = body.getAsJsonArray("whitelistRequiredSocialPlatforms");
                List<String> platforms = new ArrayList<>();
                for (JsonElement el : arr) {
                    if (el != null && el.isJsonPrimitive()) {
                        String val = el.getAsString().trim();
                        if (!val.isEmpty() && val.length() <= 32 && !platforms.contains(val)) {
                            platforms.add(val);
                        }
                    }
                }
                updates.put("whitelist_required_social_platforms", JsonHelper.toJson(platforms));
            }

            // Enforce SMTP prerequisite for email-dependent features
            boolean effSmtpEnabled = updates.containsKey("smtp_enabled")
                    ? Boolean.parseBoolean(updates.get("smtp_enabled"))
                    : Boolean.parseBoolean(SettingsService.getString("smtp_enabled", "false"));
            String effSmtpHost = updates.containsKey("smtp_host")
                    ? updates.get("smtp_host")
                    : SettingsService.getString("smtp_host", "");
            boolean smtpReady = effSmtpEnabled && effSmtpHost != null && !effSmtpHost.trim().isEmpty();
            if (!smtpReady) {
                boolean wasAlreadyOff = !SettingsService.isSmtpEnabled();
                Boolean reqEmailVer = JsonHelper.boolObj(body, "requireEmailVerification");
                Boolean reqWlEmail = JsonHelper.boolObj(body, "whitelistSendEmail");
                if (wasAlreadyOff && (Boolean.TRUE.equals(reqEmailVer) || Boolean.TRUE.equals(reqWlEmail))) {
                    ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "SMTP_REQUIRED",
                            "SMTP must be enabled and configured before enabling email verification or whitelist notification emails");
                    return;
                }
                updates.put("require_email_verification", "false");
                updates.put("whitelist_send_email", "false");
            }

            putStrIfPresent(body, "serverRestartCommand", "server_restart_command", updates);
            putStrIfPresent(body, "serverRestartHint", "server_restart_hint", updates);
            putBoolIfPresent(body, "chatSyncEnabled", "chat_sync_enabled", updates);
            putBoolIfPresent(body, "webChatSendEnabled", "web_chat_send_enabled", updates);
            putIntIfPresent(body, "chatRetentionDays", "chat_retention_days", updates, 1, 3650);

            if (!updates.isEmpty()) {
                SettingsService.setMany(updates);
            }

            Boolean reqAllowGuestWl = JsonHelper.boolObj(body, "whitelistAllowGuest");
            if (reqAllowGuestWl != null) {
                SettingsService.setGuestWhitelistApplyAllowed(reqAllowGuestWl.booleanValue());
            }

            if (Boolean.TRUE.equals(JsonHelper.boolObj(body, "clearSmtpPassword"))) {
                SettingsService.clearSmtpPassword();
            } else {
                String newPass = JsonHelper.str(body, "smtpPassword");
                if (newPass != null && !newPass.isEmpty()) {
                    SettingsService.saveSmtpPassword(newPass);
                }
            }

            // Monitor settings sync
            Boolean monEnabled = JsonHelper.boolObj(body, "monitorEnabled");
            Integer monInterval = JsonHelper.intObj(body, "monitorInterval");
            Integer monRetention = JsonHelper.intObj(body, "monitorRetentionDays");
            Boolean monShowNames = JsonHelper.boolObj(body, "monitorShowPlayerNames");
            if (monEnabled != null || monInterval != null || monRetention != null || monShowNames != null) {
                MonitorConfigService.saveAll(
                        monEnabled != null ? monEnabled : MonitorConfigService.isEnabled(),
                        monInterval != null ? monInterval : MonitorConfigService.getIntervalSeconds(),
                        monRetention != null ? monRetention : MonitorConfigService.getRetentionDays(),
                        monShowNames != null ? monShowNames : MonitorConfigService.isShowPlayerNames()
                );
            }

            Map<String, Object> result = SettingsService.getAdminSettings();
            if (reqServerWhitelist != null) {
                result.put("serverWhitelistEnabled", reqServerWhitelist);
            }
            ResponseHelper.sendOk(ctx, result);
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid settings payload");
        }
    }

    /** POST /api/admin/settings/test-smtp */
    public static void testSmtp(RequestContext ctx) {
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            SmtpMailer.SmtpConfig saved = SettingsService.getSmtpConfig();

            String passwordInput = JsonHelper.str(body, "smtpPassword");
            String finalPassword = (passwordInput != null && !passwordInput.isEmpty()) ? passwordInput : saved.getPassword();

            SmtpMailer.SmtpConfig cfg = new SmtpMailer.SmtpConfig(
                    orFallback(JsonHelper.str(body, "smtpHost"), saved.getHost()),
                    JsonHelper.intVal(body, "smtpPort", saved.getPort()),
                    orFallback(JsonHelper.str(body, "smtpEncryption"), saved.getEncryption()),
                    orFallback(JsonHelper.str(body, "smtpUser"), saved.getUsername()),
                    finalPassword,
                    orFallback(JsonHelper.str(body, "smtpFrom"), saved.getFromAddress()),
                    orFallback(JsonHelper.str(body, "smtpFromName"), saved.getFromName())
            );
            SmtpMailer.testConnection(cfg);
            ResponseHelper.sendOk(ctx, Collections.singletonMap("connected", Boolean.TRUE));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "SMTP_TEST_FAILED", e.getMessage());
        }
    }

    private static void putStrIfPresent(JsonObject body, String jsonKey, String dbKey, Map<String, String> updates) {
        if (body.has(jsonKey) && !body.get(jsonKey).isJsonNull()) {
            updates.put(dbKey, body.get(jsonKey).getAsString().trim());
        }
    }

    private static void putBoolIfPresent(JsonObject body, String jsonKey, String dbKey, Map<String, String> updates) {
        Boolean val = JsonHelper.boolObj(body, jsonKey);
        if (val != null) updates.put(dbKey, String.valueOf(val));
    }

    private static void putIntIfPresent(JsonObject body, String jsonKey, String dbKey, Map<String, String> updates, int min, int max) {
        Integer val = JsonHelper.intObj(body, jsonKey);
        if (val != null) {
            int clamped = Math.max(min, Math.min(max, val));
            updates.put(dbKey, String.valueOf(clamped));
        }
    }

    /** POST /api/admin/settings/whitelist-toggle */
    public static void toggleWhitelist(RequestContext ctx) {
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            Boolean enabled = JsonHelper.boolObj(body, "enabled");
            if (enabled == null) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "enabled is required");
                return;
            }
            WhitelistService.setServerWhitelistEnabled(enabled.booleanValue());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("serverWhitelistEnabled", enabled);
            out.put("whitelistManagementEnabled", Boolean.valueOf(SettingsService.isWhitelistManagementEnabled()));
            ResponseHelper.sendOk(ctx, out);
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", e.getMessage());
        }
    }

    /** POST /api/admin/settings/whitelist-guest-toggle */
    public static void toggleGuestWhitelist(RequestContext ctx) {
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            Boolean allow = JsonHelper.boolObj(body, "allow");
            if (allow == null) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "allow is required");
                return;
            }
            SettingsService.setGuestWhitelistApplyAllowed(allow.booleanValue());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("whitelistAllowGuest", allow);
            out.put("guestPermissions", PermissionService.getGuestPermissionLevels());
            ResponseHelper.sendOk(ctx, out);
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** GET /api/admin/worlds */
    public static void listWorlds(RequestContext ctx) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("worlds", WorldRegistryService.listWorldsWithStats());
        ResponseHelper.sendOk(ctx, out);
    }

    /** POST /api/admin/settings/favicon */
    public static void uploadFavicon(RequestContext ctx) {
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            String dataUrl = JsonHelper.str(body, "dataUrl");
            SettingsService.saveFavicon(dataUrl);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("hasCustomFavicon", Boolean.valueOf(SettingsService.hasCustomFavicon()));
            out.put("faviconVersion", SettingsService.getFaviconVersion());
            out.put("faviconUrl", SettingsService.getFaviconUrl());
            ResponseHelper.sendOk(ctx, out);
        } catch (IllegalArgumentException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_FAVICON", e.getMessage());
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid favicon payload");
        }
    }

    /** DELETE /api/admin/settings/favicon */
    public static void resetFavicon(RequestContext ctx) {
        SettingsService.resetFavicon();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hasCustomFavicon", Boolean.FALSE);
        out.put("faviconVersion", SettingsService.getFaviconVersion());
        out.put("faviconUrl", SettingsService.getFaviconUrl());
        ResponseHelper.sendOk(ctx, out);
    }

    private static void putVisIfPresent(JsonObject body, String jsonKey, String dbKey, Map<String, String> updates) {
        String val = JsonHelper.str(body, jsonKey);
        if (val != null) {
            String lower = val.trim().toLowerCase(java.util.Locale.ROOT);
            if ("readonly".equals(lower) || "full".equals(lower)) {
                updates.put(dbKey, lower);
            }
        }
    }

    private static String orFallback(String v, String fallback) {
        return v != null ? v.trim() : fallback;
    }
}

