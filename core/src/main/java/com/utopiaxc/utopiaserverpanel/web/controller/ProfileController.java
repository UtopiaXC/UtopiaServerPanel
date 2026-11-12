package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.auth.JwtClaims;
import com.utopiaxc.utopiaserverpanel.auth.JwtUtil;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.ProfileService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Collections;
import java.util.Map;

/**
 * Self-service account endpoints (/api/me/*): profile, password, 2FA, sessions, API tokens, login history.
 */
public final class ProfileController {

    private ProfileController() {}

    /** GET /api/me */
    public static void me(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        Map<String, Object> info = ProfileService.getCurrentUserInfo(userId);
        if (info == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "NOT_FOUND", "User not found");
            return;
        }
        ResponseHelper.sendOk(ctx, info);
    }

    /** PATCH /api/me/profile */
    public static void updateProfile(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            ProfileService.SimpleResult res = ProfileService.updateProfile(
                    userId.intValue(),
                    JsonHelper.str(body, "nickname"),
                    JsonHelper.str(body, "email"),
                    JsonHelper.str(body, "locale")
            );
            if (res.success()) {
                ResponseHelper.sendOk(ctx, res.data());
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, res.errorCode(), res.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** PUT /api/me/username */
    public static void changeUsername(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            ProfileService.SimpleResult res = ProfileService.changeUsername(
                    userId.intValue(),
                    JsonHelper.str(body, "newUsername"),
                    JsonHelper.str(body, "currentPassword")
            );
            if (res.success()) {
                ResponseHelper.sendOk(ctx, res.data());
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, res.errorCode(), res.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** POST /api/me/password */
    public static void changePassword(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        String sessionId = (String) ctx.getAttribute("sessionId");
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            ProfileService.SimpleResult res = ProfileService.changePassword(
                    userId.intValue(),
                    JsonHelper.str(body, "currentPassword"),
                    JsonHelper.str(body, "newPassword"),
                    sessionId
            );
            if (res.success()) {
                ResponseHelper.sendOk(ctx, Collections.singletonMap("message", res.message()));
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, res.errorCode(), res.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** POST /api/me/2fa/setup (supports normal auth OR X-MFA-Token during forced 2FA onboarding) */
    public static void setup2fa(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            String mfaToken = ctx.header("X-MFA-Token");
            if (mfaToken != null) {
                JwtClaims claims = JwtUtil.validateToken(mfaToken);
                if (claims != null && "mfa_setup".equals(JwtUtil.getPurpose(claims))) {
                    userId = Integer.valueOf(JwtUtil.getUserId(claims));
                }
            }
        }
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        ProfileService.SimpleResult res = ProfileService.beginTotpSetup(userId.intValue());
        if (res.success()) {
            ResponseHelper.sendOk(ctx, res.data());
        } else {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, res.errorCode(), res.message());
        }
    }

    /** POST /api/me/2fa/confirm */
    public static void confirm2fa(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            ProfileService.SimpleResult res = ProfileService.confirmTotpSetup(userId.intValue(), JsonHelper.str(body, "code"));
            if (res.success()) {
                ResponseHelper.sendOk(ctx, Collections.singletonMap("message", res.message()));
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, res.errorCode(), res.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** POST /api/me/2fa/disable */
    public static void disable2fa(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            ProfileService.SimpleResult res = ProfileService.disableTotp(userId.intValue(), JsonHelper.str(body, "password"), JsonHelper.str(body, "code"));
            if (res.success()) {
                ResponseHelper.sendOk(ctx, Collections.singletonMap("message", res.message()));
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, res.errorCode(), res.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** GET /api/me/sessions */
    public static void listSessions(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        String currentSessionId = (String) ctx.getAttribute("sessionId");
        ResponseHelper.sendOk(ctx, Collections.singletonMap("sessions", ProfileService.listSessions(userId.intValue(), currentSessionId)));
    }

    /** DELETE /api/me/sessions/{id} */
    public static void revokeSession(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        String sessionId = ctx.pathParam("id");
        if (sessionId == null || sessionId.trim().isEmpty()) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Session ID required");
            return;
        }
        if (ProfileService.revokeSession(userId.intValue(), sessionId)) {
            ResponseHelper.sendOk(ctx, Collections.singletonMap("message", "Session revoked"));
        } else {
            ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "NOT_FOUND", "Session not found");
        }
    }

    /** GET /api/me/login-history */
    public static void loginHistory(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        ResponseHelper.sendOk(ctx, Collections.singletonMap("history", ProfileService.listLoginHistory(userId.intValue(), 20)));
    }

    /** GET /api/me/tokens */
    public static void listTokens(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        ResponseHelper.sendOk(ctx, Collections.singletonMap("tokens", ProfileService.listApiTokens(userId.intValue())));
    }

    /** POST /api/me/tokens */
    public static void createToken(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            ProfileService.SimpleResult res = ProfileService.createApiToken(
                    userId.intValue(),
                    JsonHelper.str(body, "name"),
                    JsonHelper.intObj(body, "expiresDays"),
                    JsonHelper.str(body, "scopes")
            );
            if (res.success()) {
                ResponseHelper.sendOk(ctx, res.data());
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, res.errorCode(), res.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** DELETE /api/me/tokens/{id} */
    public static void revokeToken(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        int tokenId;
        try {
            tokenId = Integer.parseInt(ctx.pathParam("id"));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_ID", "Invalid token ID");
            return;
        }
        if (ProfileService.revokeApiToken(userId.intValue(), tokenId)) {
            ResponseHelper.sendOk(ctx, Collections.singletonMap("message", "Token revoked"));
        } else {
            ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "NOT_FOUND", "Token not found");
        }
    }
}

