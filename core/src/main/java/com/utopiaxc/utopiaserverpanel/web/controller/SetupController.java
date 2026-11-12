package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.config.PanelConfig;
import com.utopiaxc.utopiaserverpanel.mail.SmtpMailer;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.SetupService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SetupController {

    private SetupController() {}

    /** GET /api/setup/status */
    public static void status(RequestContext ctx) {
        boolean required = SetupService.isSetupRequired();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("setupRequired", Boolean.valueOf(required));
        out.put("setupCompleted", Boolean.valueOf(!required));
        out.put("configFile", PanelConfig.getConfigDisplayPath());
        ResponseHelper.sendOk(ctx, out);
    }

    /** POST /api/setup/verify-token */
    public static void verifyToken(RequestContext ctx) {
        if (!SetupService.isSetupRequired()) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "SETUP_ALREADY_COMPLETED", "Setup is already completed");
            return;
        }
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            String token = JsonHelper.str(body, "token");
            if (token == null || token.trim().isEmpty()) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "TOKEN_REQUIRED", "Setup token is required");
                return;
            }
            if (SetupService.verifyToken(ctx.clientIp(), token)) {
                ResponseHelper.sendOk(ctx, Collections.singletonMap("valid", Boolean.TRUE));
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "INVALID_SETUP_TOKEN", "Invalid setup token");
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** POST /api/setup/test-smtp */
    public static void testSmtp(RequestContext ctx) {
        if (!SetupService.isSetupRequired()) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "SETUP_ALREADY_COMPLETED", "Setup is already completed");
            return;
        }
        String token = ctx.header("X-Setup-Token");
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            if (token == null || token.trim().isEmpty()) token = JsonHelper.str(body, "token");
            if (!SetupService.verifyToken(ctx.clientIp(), token)) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "INVALID_SETUP_TOKEN", "Invalid setup token");
                return;
            }
            SmtpMailer.SmtpConfig cfg = new SmtpMailer.SmtpConfig(
                    JsonHelper.str(body, "smtpHost"),
                    JsonHelper.intVal(body, "smtpPort", 465),
                    JsonHelper.str(body, "smtpEncryption"),
                    JsonHelper.str(body, "smtpUser"),
                    JsonHelper.str(body, "smtpPassword"),
                    JsonHelper.str(body, "smtpFrom"),
                    JsonHelper.str(body, "smtpFromName")
            );
            SmtpMailer.testConnection(cfg);
            ResponseHelper.sendOk(ctx, Collections.singletonMap("connected", Boolean.TRUE));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "SMTP_TEST_FAILED", e.getMessage());
        }
    }

    /** POST /api/setup/complete */
    public static void complete(RequestContext ctx) {
        if (!SetupService.isSetupRequired()) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "SETUP_ALREADY_COMPLETED", "Setup is already completed");
            return;
        }
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            String token = ctx.header("X-Setup-Token");
            if (token == null || token.trim().isEmpty()) token = JsonHelper.str(body, "token");

            SetupService.SetupRequest req = new SetupService.SetupRequest(
                    token,
                    JsonHelper.str(body, "siteName"),
                    JsonHelper.str(body, "siteDescription"),
                    JsonHelper.str(body, "baseUrl"),
                    JsonHelper.str(body, "defaultLocale"),
                    JsonHelper.boolVal(body, "smtpEnabled", false),
                    JsonHelper.str(body, "smtpHost"),
                    JsonHelper.intVal(body, "smtpPort", 465),
                    JsonHelper.str(body, "smtpEncryption"),
                    JsonHelper.str(body, "smtpUser"),
                    JsonHelper.str(body, "smtpPassword"),
                    JsonHelper.str(body, "smtpFrom"),
                    JsonHelper.str(body, "smtpFromName"),
                    JsonHelper.str(body, "adminUsername"),
                    JsonHelper.str(body, "adminNickname"),
                    JsonHelper.str(body, "adminEmail"),
                    JsonHelper.str(body, "adminPassword")
            );

            SetupService.SetupResult res = SetupService.completeSetup(ctx.clientIp(), req);
            if (res.success()) {
                ResponseHelper.sendOk(ctx, Collections.singletonMap("message", res.message()));
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, res.errorCode(), res.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }
}

