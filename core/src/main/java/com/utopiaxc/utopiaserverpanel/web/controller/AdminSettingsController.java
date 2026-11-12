package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.mail.SmtpMailer;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.MonitorConfigService;
import com.utopiaxc.utopiaserverpanel.web.service.SettingsService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
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

            if (!updates.isEmpty()) {
                SettingsService.setMany(updates);
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

            ResponseHelper.sendOk(ctx, SettingsService.getAdminSettings());
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

    private static String orFallback(String v, String fallback) {
        return v != null ? v.trim() : fallback;
    }
}

