package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.db.model.PermissionDef;
import com.utopiaxc.utopiaserverpanel.web.db.model.RoleInfo;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionService;
import com.utopiaxc.utopiaserverpanel.web.service.RoleService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Administrative RBAC role & extensible permission catalog endpoints.
 */
public final class AdminRoleController {

    private AdminRoleController() {}

    /** GET /api/admin/roles */
    public static void listRoles(RequestContext ctx) {
        ResponseHelper.sendOk(ctx, Collections.singletonMap("roles", RoleService.listRoles()));
    }

    /** GET /api/admin/roles/{id} */
    public static void getRole(RequestContext ctx) {
        int roleId;
        try {
            roleId = Integer.parseInt(ctx.pathParam("id"));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_ID", "Invalid role ID");
            return;
        }
        RoleInfo role = RoleService.getRole(roleId);
        if (role == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "NOT_FOUND", "Role not found");
            return;
        }
        ResponseHelper.sendOk(ctx, role);
    }

    /** POST /api/admin/roles */
    public static void createRole(RequestContext ctx) {
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            Map<String, Integer> levels = parseLevels(body);
            RoleService.CreateRoleResult res = RoleService.createRole(
                    JsonHelper.str(body, "name"),
                    JsonHelper.str(body, "displayName"),
                    JsonHelper.str(body, "description"),
                    levels
            );
            if (res.success()) {
                ResponseHelper.sendOk(ctx, res.role());
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, res.errorCode(), res.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** PUT /api/admin/roles/{id} */
    public static void updateRole(RequestContext ctx) {
        int roleId;
        try {
            roleId = Integer.parseInt(ctx.pathParam("id"));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_ID", "Invalid role ID");
            return;
        }
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            Map<String, Integer> levels = body.has("permissionLevels") ? parseLevels(body) : null;
            RoleService.UpdateResult res = RoleService.updateRole(
                    roleId,
                    JsonHelper.str(body, "name"),
                    JsonHelper.str(body, "displayName"),
                    JsonHelper.str(body, "description"),
                    levels
            );
            switch (res.status()) {
                case SUCCESS:
                    ResponseHelper.sendOk(ctx, res.role());
                    break;
                case NOT_FOUND:
                    ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "NOT_FOUND", "Role not found");
                    break;
                case IMMUTABLE:
                    ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "IMMUTABLE_ROLE", "Administrator role permissions cannot be modified");
                    break;
                case DUPLICATE_NAME:
                    ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "ROLE_EXISTS", "Role name already exists");
                    break;
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** DELETE /api/admin/roles/{id} */
    public static void deleteRole(RequestContext ctx) {
        int roleId;
        try {
            roleId = Integer.parseInt(ctx.pathParam("id"));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_ID", "Invalid role ID");
            return;
        }
        RoleService.DeleteRoleResult res = RoleService.deleteRole(roleId);
        switch (res) {
            case SUCCESS:
                ResponseHelper.sendOk(ctx, Collections.singletonMap("message", "Role deleted"));
                break;
            case NOT_FOUND:
                ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "NOT_FOUND", "Role not found");
                break;
            case SYSTEM_ROLE:
                ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "SYSTEM_ROLE", "Cannot delete system roles");
                break;
            case HAS_USERS:
                ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "ROLE_IN_USE", "Cannot delete role assigned to users");
                break;
        }
    }

    /** GET /api/admin/permissions */
    public static void listPermissions(RequestContext ctx) {
        List<PermissionDef> defs = PermissionService.listPermissionDefinitions();
        List<Map<String, Object>> levelList = new ArrayList<>();
        levelList.add(levelOption(0, "deny"));
        levelList.add(levelOption(1, "readonly"));
        levelList.add(levelOption(2, "full"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("permissions", defs);
        result.put("levels", levelList);
        ResponseHelper.sendOk(ctx, result);
    }

    private static Map<String, Object> levelOption(int value, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", Integer.valueOf(value));
        m.put("label", label);
        return m;
    }

    private static Map<String, Integer> parseLevels(JsonObject body) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        if (body.has("permissionLevels") && body.get("permissionLevels").isJsonObject()) {
            JsonObject obj = body.getAsJsonObject("permissionLevels");
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isJsonNull()) {
                    levels.put(entry.getKey(), Integer.valueOf(entry.getValue().getAsInt()));
                }
            }
        }
        return levels;
    }
}

