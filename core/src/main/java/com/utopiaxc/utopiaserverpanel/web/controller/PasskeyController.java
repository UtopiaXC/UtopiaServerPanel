package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.auth.WebAuthnUtil;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.AuthService;
import com.utopiaxc.utopiaserverpanel.web.service.PasskeyService;
import com.utopiaxc.utopiaserverpanel.web.service.SetupService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Collections;
import java.util.Map;

/**
 * WebAuthn Passkey endpoints (/api/me/passkeys/* & /api/auth/passkey/* & /api/auth/mfa/passkey/*).
 */
public final class PasskeyController {

    private PasskeyController() {}

    /** GET /api/me/passkeys */
    public static void listMine(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        ResponseHelper.sendOk(ctx, Collections.singletonMap("passkeys", PasskeyService.listByUser(userId.intValue())));
    }

    /** POST /api/me/passkeys/register/options (supports auth OR X-MFA-Token for forced 2FA onboarding) */
    public static void beginRegister(RequestContext ctx) {
        Integer userId = resolveUserOrSetupMfa(ctx);
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        String rpId = WebAuthnUtil.resolveRpId(ctx);
        PasskeyService.ServiceResult res = PasskeyService.beginRegistration(userId.intValue(), rpId);
        sendResult(ctx, res);
    }

    /** POST /api/me/passkeys/register/verify (supports auth OR X-MFA-Token for forced 2FA onboarding) */
    public static void finishRegister(RequestContext ctx) {
        Integer userId = resolveUserOrSetupMfa(ctx);
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            String challengeId = JsonHelper.str(body, "challengeId");
            String name = JsonHelper.str(body, "name");
            JsonObject credential = JsonHelper.obj(body, "credential");
            String mfaSetupToken = (ctx.getAttribute("userId") == null) ? ctx.header("X-MFA-Token") : null;
            PasskeyService.ServiceResult res = PasskeyService.finishRegistration(
                    userId.intValue(), challengeId, name, credential,
                    ctx.header("Origin"), mfaSetupToken, ctx.clientIp(), ctx.userAgent());
            sendResult(ctx, res);
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** PUT /api/me/passkeys/{id} */
    public static void renameMine(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            JsonObject body = JsonHelper.parseObject(ctx.body());
            sendResult(ctx, PasskeyService.renamePasskey(userId.intValue(), id, JsonHelper.str(body, "name")));
        } catch (NumberFormatException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_ID", "Invalid passkey ID");
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** DELETE /api/me/passkeys/{id} */
    public static void deleteMine(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            sendResult(ctx, PasskeyService.deletePasskey(userId.intValue(), id));
        } catch (NumberFormatException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_ID", "Invalid passkey ID");
        }
    }

    /** POST /api/auth/passkey/login/options */
    public static void beginLogin(RequestContext ctx) {
        if (SetupService.isSetupRequired()) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "SETUP_REQUIRED", "Panel setup is not completed");
            return;
        }
        String accountHint = null;
        if (ctx.body() != null && !ctx.body().trim().isEmpty()) {
            try {
                JsonObject body = JsonHelper.parseObject(ctx.body());
                accountHint = JsonHelper.str(body, "username");
            } catch (Exception ignored) {
                // optional body
            }
        }
        String rpId = WebAuthnUtil.resolveRpId(ctx);
        sendResult(ctx, PasskeyService.beginLogin(accountHint, rpId));
    }

    /** POST /api/auth/passkey/login/verify */
    public static void finishLogin(RequestContext ctx) {
        if (SetupService.isSetupRequired()) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "SETUP_REQUIRED", "Panel setup is not completed");
            return;
        }
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            String challengeId = JsonHelper.str(body, "challengeId");
            JsonObject credential = JsonHelper.obj(body, "credential");
            boolean rememberMe = JsonHelper.boolVal(body, "rememberMe", false);
            sendResult(ctx, PasskeyService.finishLogin(
                    challengeId, credential, rememberMe, ctx.header("Origin"), ctx.clientIp(), ctx.userAgent()));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** POST /api/auth/mfa/passkey/options */
    public static void beginMfa(RequestContext ctx) {
        if (SetupService.isSetupRequired()) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "SETUP_REQUIRED", "Panel setup is not completed");
            return;
        }
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            String mfaToken = JsonHelper.str(body, "mfaToken");
            String rpId = WebAuthnUtil.resolveRpId(ctx);
            sendResult(ctx, PasskeyService.beginMfa(mfaToken, rpId));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** POST /api/auth/mfa/passkey/verify */
    public static void finishMfa(RequestContext ctx) {
        if (SetupService.isSetupRequired()) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "SETUP_REQUIRED", "Panel setup is not completed");
            return;
        }
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            String challengeId = JsonHelper.str(body, "challengeId");
            String mfaToken = JsonHelper.str(body, "mfaToken");
            JsonObject credential = JsonHelper.obj(body, "credential");
            sendResult(ctx, PasskeyService.finishMfa(
                    challengeId, mfaToken, credential, ctx.header("Origin"), ctx.clientIp(), ctx.userAgent()));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    private static Integer resolveUserOrSetupMfa(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId != null) return userId;
        String mfaToken = ctx.header("X-MFA-Token");
        AuthService.ValidatedMfaToken validated = AuthService.validateMfaToken(mfaToken, "mfa_setup");
        return validated != null ? Integer.valueOf(validated.user.getId()) : null;
    }

    private static void sendResult(RequestContext ctx, PasskeyService.ServiceResult res) {
        if (res.success()) {
            Map<String, Object> data = res.data() != null ? res.data() : Collections.emptyMap();
            ResponseHelper.sendOk(ctx, data);
        } else {
            ResponseHelper.sendError(ctx, HttpResponseStatus.valueOf(res.httpStatus()), res.errorCode(), res.message());
        }
    }
}
