package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.AuthService;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionService;
import com.utopiaxc.utopiaserverpanel.web.service.SetupService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AuthController {

    private AuthController() {}

    /** POST /api/auth/login */
    public static void login(RequestContext ctx) {
        if (SetupService.isSetupRequired()) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "SETUP_REQUIRED", "Panel setup is not completed");
            return;
        }
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            AuthService.LoginRequest req = new AuthService.LoginRequest(
                    JsonHelper.str(body, "username"),
                    JsonHelper.str(body, "password"),
                    JsonHelper.boolVal(body, "rememberMe", false),
                    JsonHelper.str(body, "captcha"),
                    JsonHelper.str(body, "totpCode"),
                    JsonHelper.str(body, "mfaToken"),
                    JsonHelper.str(body, "setupTotpCode")
            );
            AuthService.LoginResult res = AuthService.login(req, ctx.clientIp(), ctx.userAgent());
            if (res.success()) {
                ResponseHelper.sendOk(ctx, res.data());
                return;
            }
            if (res.mfaRequired() || res.setup2faRequired()) {
                Map<String, Object> challenge = new HashMap<>();
                challenge.put("mfaRequired", Boolean.valueOf(res.mfaRequired()));
                challenge.put("setup2faRequired", Boolean.valueOf(res.setup2faRequired()));
                challenge.put("mfaToken", res.mfaToken());
                ResponseHelper.sendOk(ctx, challenge);
                return;
            }
            Map<String, Object> extra = new HashMap<>();
            if (res.captchaRequired()) extra.put("captchaRequired", Boolean.TRUE);
            if (res.retryAfterSeconds() != null) extra.put("retryAfterSeconds", res.retryAfterSeconds());
            ResponseHelper.sendError(ctx, HttpResponseStatus.valueOf(res.httpStatus()), res.errorCode(), res.message(), extra);
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** POST /api/auth/register */
    public static void register(RequestContext ctx) {
        if (SetupService.isSetupRequired()) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "SETUP_REQUIRED", "Panel setup is not completed");
            return;
        }
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            AuthService.RegisterRequest req = new AuthService.RegisterRequest(
                    JsonHelper.str(body, "username"),
                    JsonHelper.str(body, "nickname"),
                    JsonHelper.str(body, "email"),
                    JsonHelper.str(body, "password"),
                    JsonHelper.str(body, "bindingCode"),
                    JsonHelper.str(body, "captcha")
            );
            String locale = ctx.header("X-Locale");
            AuthService.ActionResult res = AuthService.register(req, ctx.inferredBaseUrl(), locale);
            if (res.success()) {
                Map<String, Object> bodyOut = new LinkedHashMap<>();
                bodyOut.put("message", res.message());
                bodyOut.put("activationRequired", Boolean.valueOf(res.activationRequired()));
                ResponseHelper.sendOk(ctx, bodyOut);
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, res.errorCode(), res.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** POST /api/auth/activate */
    public static void activate(RequestContext ctx) {
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            String token = JsonHelper.str(body, "token");
            AuthService.ActionResult res = AuthService.activateAccount(token);
            if (res.success()) {
                ResponseHelper.sendOk(ctx, Collections.singletonMap("message", res.message()));
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, res.errorCode(), res.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** POST /api/auth/refresh */
    public static void refresh(RequestContext ctx) {
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            String refreshToken = JsonHelper.str(body, "refreshToken");
            Map<String, Object> result = AuthService.refreshSession(refreshToken, ctx.clientIp(), ctx.userAgent());
            if (result == null) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Session expired");
                return;
            }
            ResponseHelper.sendOk(ctx, result);
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** POST /api/auth/logout */
    public static void logout(RequestContext ctx) {
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            String refreshToken = JsonHelper.str(body, "refreshToken");
            Integer userId = (Integer) ctx.getAttribute("userId");
            String sessionId = (String) ctx.getAttribute("sessionId");
            AuthService.logout(refreshToken, sessionId, userId);
        } catch (Exception ignored) {}
        ResponseHelper.sendOk(ctx, Collections.singletonMap("message", "Logged out"));
    }

    /** POST /api/auth/forgot-password */
    public static void forgotPassword(RequestContext ctx) {
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            String account = JsonHelper.str(body, "account");
            String captcha = JsonHelper.str(body, "captcha");
            AuthService.ActionResult res = AuthService.sendRecoveryEmail(account, "password", captcha, ctx.inferredBaseUrl(), ctx.header("X-Locale"));
            if (res.success()) {
                ResponseHelper.sendOk(ctx, Collections.singletonMap("message", res.message()));
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, res.errorCode(), res.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** POST /api/auth/reset-password */
    public static void resetPassword(RequestContext ctx) {
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            String token = JsonHelper.str(body, "token");
            String newPassword = JsonHelper.str(body, "newPassword");
            AuthService.ActionResult res = AuthService.resetPasswordWithToken(token, newPassword);
            if (res.success()) {
                ResponseHelper.sendOk(ctx, Collections.singletonMap("message", res.message()));
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, res.errorCode(), res.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** POST /api/auth/forgot-2fa */
    public static void forgot2fa(RequestContext ctx) {
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            String account = JsonHelper.str(body, "account");
            String captcha = JsonHelper.str(body, "captcha");
            AuthService.ActionResult res = AuthService.sendRecoveryEmail(account, "2fa", captcha, ctx.inferredBaseUrl(), ctx.header("X-Locale"));
            if (res.success()) {
                ResponseHelper.sendOk(ctx, Collections.singletonMap("message", res.message()));
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, res.errorCode(), res.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** POST /api/auth/reset-2fa */
    public static void reset2fa(RequestContext ctx) {
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            String token = JsonHelper.str(body, "token");
            AuthService.ActionResult res = AuthService.reset2faWithToken(token);
            if (res.success()) {
                ResponseHelper.sendOk(ctx, Collections.singletonMap("message", res.message()));
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, res.errorCode(), res.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** GET /api/auth/guest-permissions */
    public static void guestPermissions(RequestContext ctx) {
        ResponseHelper.sendOk(ctx, Collections.singletonMap("permissions", PermissionService.getGuestPermissionLevels()));
    }
}
