package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.Config;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.AuthService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Map;
import java.util.Set;

/**
 * HTTP controller for authentication endpoints.
 * Handles login, token refresh, logout, password change, registration, and user info.
 */
public final class AuthController {
    private static final Gson GSON = new Gson();

    private AuthController() {}

    /**
     * POST /api/auth/login
     * Body: { "username": "...", "password": "..." }
     */
    public static void login(RequestContext ctx) {
        try {
            JsonObject body = GSON.fromJson(ctx.body(), JsonObject.class);
            String username = getString(body, "username");
            String password = getString(body, "password");

            if (username == null || password == null) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Username and password are required");
                return;
            }

            AuthService.AuthResult result = AuthService.login(username, password);
            if (result == null) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "Invalid username or password");
                return;
            }

            if (result.needsBinding()) {
                JsonObject data = new JsonObject();
                data.addProperty("needsBinding", true);
                data.addProperty("message", result.errorMessage());
                data.addProperty("userId", result.userId());
                data.addProperty("username", result.username());
                ResponseHelper.sendOk(ctx, data);
                return;
            }

            JsonObject data = new JsonObject();
            data.addProperty("accessToken", result.accessToken());
            data.addProperty("refreshToken", result.refreshToken());
            data.addProperty("mustChangePassword", result.mustChangePassword());
            data.add("user", GSON.toJsonTree(result.user()));
            ResponseHelper.sendOk(ctx, data);
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid request body");
        }
    }

    /**
     * POST /api/auth/refresh
     * Body: { "refreshToken": "..." }
     */
    public static void refresh(RequestContext ctx) {
        try {
            JsonObject body = GSON.fromJson(ctx.body(), JsonObject.class);
            String refreshToken = getString(body, "refreshToken");

            if (refreshToken == null) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Refresh token is required");
                return;
            }

            AuthService.AuthResult result = AuthService.refreshToken(refreshToken);
            if (result == null || !result.isSuccess()) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "Invalid or expired refresh token");
                return;
            }

            JsonObject data = new JsonObject();
            data.addProperty("accessToken", result.accessToken());
            data.addProperty("refreshToken", result.refreshToken());
            ResponseHelper.sendOk(ctx, data);
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid request body");
        }
    }

    /**
     * POST /api/auth/logout
     * Body: { "refreshToken": "..." }
     */
    public static void logout(RequestContext ctx) {
        try {
            JsonObject body = GSON.fromJson(ctx.body(), JsonObject.class);
            String refreshToken = getString(body, "refreshToken");

            if (refreshToken != null) {
                AuthService.logout(refreshToken);
            }
            ResponseHelper.sendOk(ctx, Map.of("message", "Logged out"));
        } catch (Exception e) {
            ResponseHelper.sendOk(ctx, Map.of("message", "Logged out"));
        }
    }

    /**
     * POST /api/auth/change-password
     * Body: { "oldPassword": "...", "newPassword": "..." }
     * Requires authentication.
     */
    public static void changePassword(RequestContext ctx) {
        try {
            Integer userId = (Integer) ctx.getAttribute("userId");
            if (userId == null) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "Authentication required");
                return;
            }

            JsonObject body = GSON.fromJson(ctx.body(), JsonObject.class);
            String oldPassword = getString(body, "oldPassword");
            String newPassword = getString(body, "newPassword");

            if (oldPassword == null || newPassword == null) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Old and new passwords are required");
                return;
            }

            if (newPassword.length() < 4) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "New password must be at least 4 characters");
                return;
            }

            AuthService.PasswordChangeResult result = AuthService.changePassword(userId, oldPassword, newPassword);
            switch (result) {
                case SUCCESS -> ResponseHelper.sendOk(ctx, Map.of("message", "Password changed successfully"));
                case WRONG_PASSWORD -> ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Current password is incorrect");
                case ERROR -> ResponseHelper.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Failed to change password");
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid request body");
        }
    }

    /**
     * POST /api/auth/register
     * Body: { "username": "...", "password": "...", "bindingCode": "..." }
     */
    public static void register(RequestContext ctx) {
        // Check registration enabled
        if (!Config.ALLOW_REGISTRATION.get()) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "Registration is disabled");
            return;
        }

        try {
            JsonObject body = GSON.fromJson(ctx.body(), JsonObject.class);
            String username = getString(body, "username");
            String password = getString(body, "password");
            String bindingCode = getString(body, "bindingCode");

            if (username == null || password == null || bindingCode == null) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Username, password, and binding code are required");
                return;
            }

            AuthService.RegisterResult result = AuthService.register(username, password, bindingCode);
            if (result.success()) {
                ResponseHelper.sendOk(ctx, Map.of("message", result.message()));
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, result.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid request body");
        }
    }

    /**
     * GET /api/auth/me
     * Returns current user info and permissions.
     * Requires authentication.
     */
    public static void me(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "Authentication required");
            return;
        }

        Map<String, Object> userInfo = AuthService.getUserInfo(userId);
        if (userInfo == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "User not found");
            return;
        }

        ResponseHelper.sendOk(ctx, userInfo);
    }

    /**
     * GET /api/auth/permissions
     * Returns current user's permission keys.
     * Requires authentication.
     */
    public static void permissions(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "Authentication required");
            return;
        }

        Set<String> perms = com.utopiaxc.utopiaserverpanel.web.service.PermissionService.getUserPermissions(userId);
        ResponseHelper.sendOk(ctx, Map.of("permissions", perms));
    }

    private static String getString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }
}
