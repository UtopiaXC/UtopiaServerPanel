package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.Config;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.AuthService;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Map;

/**
 * HTTP controller for authentication endpoints.
 * Handles login, token refresh, logout, password change, registration,
 * user info, permissions, and username change.
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

            // Record login history
            try {
                String ipAddress = ctx.nettyCtx().channel().remoteAddress() != null
                        ? ctx.nettyCtx().channel().remoteAddress().toString() : null;
                AuthService.recordLogin(result.userId(), ipAddress);
            } catch (Exception ignored) {}

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
     * PUT /api/auth/username
     * Body: { "newUsername": "..." }
     * Requires authentication.
     */
    public static void changeUsername(RequestContext ctx) {
        try {
            Integer userId = (Integer) ctx.getAttribute("userId");
            if (userId == null) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "Authentication required");
                return;
            }

            JsonObject body = GSON.fromJson(ctx.body(), JsonObject.class);
            String newUsername = getString(body, "newUsername");

            if (newUsername == null || newUsername.length() < 3 || newUsername.length() > 32) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Username must be 3-32 characters");
                return;
            }

            if (!newUsername.matches("[a-zA-Z0-9_]+")) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Username can only contain letters, numbers, and underscores");
                return;
            }

            boolean success = AuthService.changeUsername(userId, newUsername);
            if (success) {
                ResponseHelper.sendOk(ctx, Map.of("message", "Username changed successfully"));
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Username already taken");
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
     * Returns current user info and permission levels.
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

        // Replace old permissions list with level-based map
        Map<String, Integer> levels = PermissionService.getUserPermissionLevels(userId);
        userInfo.put("permissions", levels);

        ResponseHelper.sendOk(ctx, userInfo);
    }

    /**
     * GET /api/auth/permissions
     * Returns current user's permission levels.
     * Requires authentication.
     */
    public static void permissions(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "Authentication required");
            return;
        }

        Map<String, Integer> levels = PermissionService.getUserPermissionLevels(userId);
        ResponseHelper.sendOk(ctx, Map.of("permissions", levels));
    }

    /**
     * GET /api/auth/guest-permissions
     * Returns the guest role's permission levels (for unauthenticated visitors).
     * No authentication required.
     */
    public static void guestPermissions(RequestContext ctx) {
        Map<String, Integer> levels = PermissionService.getGuestPermissionLevels();
        ResponseHelper.sendOk(ctx, Map.of("permissions", levels));
    }

    private static String getString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }
}
