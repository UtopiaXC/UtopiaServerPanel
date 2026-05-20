package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionLevel;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionService;
import com.utopiaxc.utopiaserverpanel.web.service.RoleService;
import com.utopiaxc.utopiaserverpanel.web.service.UserService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.*;

/**
 * HTTP controller for admin endpoints: user management, role management, permissions.
 */
public final class AdminController {
    private static final Gson GSON = new Gson();

    private AdminController() {}

    // ────────────── Users ──────────────

    /** GET /api/admin/users */
    public static void listUsers(RequestContext ctx) {
        List<Map<String, Object>> users = UserService.listUsers();
        ResponseHelper.sendOk(ctx, Map.of("users", users));
    }

    /** POST /api/admin/users */
    public static void createUser(RequestContext ctx) {
        try {
            JsonObject body = GSON.fromJson(ctx.body(), JsonObject.class);
            String username = getString(body, "username");
            String password = getString(body, "password");
            int roleId = body.has("roleId") ? body.get("roleId").getAsInt() : 2;

            if (username == null || password == null) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Username and password are required");
                return;
            }

            UserService.CreateUserResult result = UserService.createUser(username, password, roleId);
            if (result.isSuccess()) {
                ResponseHelper.sendOk(ctx, Map.of("userId", result.userId(), "message", result.message()));
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, result.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid request body");
        }
    }

    /** PUT /api/admin/users/{id} */
    public static void updateUser(RequestContext ctx) {
        String userIdStr = ctx.pathParam("id");
        if (userIdStr == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "User ID is required");
            return;
        }
        int userId;
        try { userId = Integer.parseInt(userIdStr); } catch (NumberFormatException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid user ID");
            return;
        }

        try {
            JsonObject body = GSON.fromJson(ctx.body(), JsonObject.class);
            Integer roleId = body.has("roleId") ? body.get("roleId").getAsInt() : null;
            Boolean isActive = body.has("isActive") ? body.get("isActive").getAsBoolean() : null;

            // Handle password reset
            if (body.has("newPassword") && !body.get("newPassword").isJsonNull()) {
                String newPassword = body.get("newPassword").getAsString();
                if (newPassword.length() < 4) {
                    ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Password must be at least 4 characters");
                    return;
                }
                if (!UserService.resetPassword(userId, newPassword)) {
                    ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "User not found");
                    return;
                }
            }

            if (roleId != null || isActive != null) {
                if (!UserService.updateUser(userId, roleId, isActive)) {
                    ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "User not found or cannot be modified");
                    return;
                }
            }

            Map<String, Object> updated = UserService.getUser(userId);
            if (updated == null) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "User not found");
                return;
            }
            ResponseHelper.sendOk(ctx, updated);
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid request body");
        }
    }

    /** DELETE /api/admin/users/{id} */
    public static void deleteUser(RequestContext ctx) {
        String userIdStr = ctx.pathParam("id");
        if (userIdStr == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "User ID is required");
            return;
        }
        int userId;
        try { userId = Integer.parseInt(userIdStr); } catch (NumberFormatException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid user ID");
            return;
        }

        Integer requestingUserId = (Integer) ctx.getAttribute("userId");
        if (requestingUserId == null) requestingUserId = -1;

        UserService.DeleteResult result = UserService.deleteUser(userId, requestingUserId);
        switch (result) {
            case SUCCESS -> ResponseHelper.sendOk(ctx, Map.of("message", "User deleted"));
            case NOT_FOUND -> ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "User not found");
            case CANNOT_DELETE_ADMIN -> ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "Cannot delete the admin user");
            case CANNOT_DELETE_SELF -> ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "Cannot delete your own account");
            case ERROR -> ResponseHelper.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Failed to delete user");
        }
    }

    // ────────────── Roles ──────────────

    /** GET /api/admin/roles */
    public static void listRoles(RequestContext ctx) {
        List<Map<String, Object>> roles = RoleService.listRoles();
        ResponseHelper.sendOk(ctx, Map.of("roles", roles));
    }

    /** GET /api/admin/roles/{id} */
    public static void getRole(RequestContext ctx) {
        String roleIdStr = ctx.pathParam("id");
        if (roleIdStr == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Role ID is required");
            return;
        }
        int roleId;
        try { roleId = Integer.parseInt(roleIdStr); } catch (NumberFormatException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid role ID");
            return;
        }

        Map<String, Object> role = RoleService.getRole(roleId);
        if (role == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "Role not found");
            return;
        }
        ResponseHelper.sendOk(ctx, role);
    }

    /** POST /api/admin/roles */
    public static void createRole(RequestContext ctx) {
        try {
            JsonObject body = GSON.fromJson(ctx.body(), JsonObject.class);
            String name = getString(body, "name");
            String description = getString(body, "description");

            if (name == null || name.isBlank()) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Role name is required");
                return;
            }

            Map<String, Integer> permissionLevels = new LinkedHashMap<>();
            if (body.has("permissionLevels")) {
                JsonObject levels = body.getAsJsonObject("permissionLevels");
                for (String key : levels.keySet()) {
                    permissionLevels.put(key, levels.get(key).getAsInt());
                }
            }

            int roleId = RoleService.createRole(name.trim(), description, permissionLevels);
            if (roleId >= 0) {
                Map<String, Object> role = RoleService.getRole(roleId);
                ResponseHelper.sendOk(ctx, role);
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Role name already exists");
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid request body");
        }
    }

    /** PUT /api/admin/roles/{id} */
    public static void updateRole(RequestContext ctx) {
        String roleIdStr = ctx.pathParam("id");
        if (roleIdStr == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Role ID is required");
            return;
        }
        int roleId;
        try { roleId = Integer.parseInt(roleIdStr); } catch (NumberFormatException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid role ID");
            return;
        }

        try {
            JsonObject body = GSON.fromJson(ctx.body(), JsonObject.class);
            String name = getString(body, "name");
            String description = getString(body, "description");
            Map<String, Integer> permissionLevels = null;

            if (body.has("permissionLevels")) {
                JsonObject levels = body.getAsJsonObject("permissionLevels");
                permissionLevels = new LinkedHashMap<>();
                for (String key : levels.keySet()) {
                    permissionLevels.put(key, levels.get(key).getAsInt());
                }
            }

            RoleService.UpdateRoleResult result = RoleService.updateRole(roleId, name, description, permissionLevels);
            switch (result) {
                case SUCCESS -> {
                    Map<String, Object> role = RoleService.getRole(roleId);
                    ResponseHelper.sendOk(ctx, role);
                }
                case NOT_FOUND -> ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "Role not found");
                case IMMUTABLE -> ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "This role's permissions cannot be modified");
                case ERROR -> ResponseHelper.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Failed to update role");
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid request body");
        }
    }

    /** DELETE /api/admin/roles/{id} */
    public static void deleteRole(RequestContext ctx) {
        String roleIdStr = ctx.pathParam("id");
        if (roleIdStr == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Role ID is required");
            return;
        }
        int roleId;
        try { roleId = Integer.parseInt(roleIdStr); } catch (NumberFormatException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid role ID");
            return;
        }

        RoleService.DeleteRoleResult result = RoleService.deleteRole(roleId);
        switch (result) {
            case SUCCESS -> ResponseHelper.sendOk(ctx, Map.of("message", "Role deleted"));
            case NOT_FOUND -> ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "Role not found");
            case SYSTEM_ROLE -> ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "Cannot delete system roles");
            case HAS_USERS -> ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "Cannot delete role with assigned users");
            case ERROR -> ResponseHelper.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Failed to delete role");
        }
    }

    // ────────────── Permissions ──────────────

    /** GET /api/admin/permissions - Returns the 4 permission categories with descriptions. */
    public static void listPermissions(RequestContext ctx) {
        // Return the 4 permission categories with their descriptions
        List<Map<String, String>> permCategories = new ArrayList<>();
        for (String key : PermissionLevel.PERMISSION_KEYS) {
            Map<String, String> cat = new LinkedHashMap<>();
            cat.put("key", key);
            switch (key) {
                case "admin" -> cat.put("description", "Administration panel access");
                case "dashboard" -> cat.put("description", "Server dashboard and status");
                case "terminal" -> cat.put("description", "Server console and commands");
                case "logs" -> cat.put("description", "Monitoring logs");
            }
            permCategories.add(cat);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", permCategories);
        result.put("levels", List.of(
            Map.of("value", 0, "label", "deny"),
            Map.of("value", 1, "label", "readonly"),
            Map.of("value", 2, "label", "full")
        ));
        ResponseHelper.sendOk(ctx, result);
    }

    // ────────────── Helpers ──────────────

    private static String getString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }
}
