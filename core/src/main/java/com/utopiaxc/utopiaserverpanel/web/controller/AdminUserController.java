package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.BindingService;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionService;
import com.utopiaxc.utopiaserverpanel.web.service.UserService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Collections;
import java.util.Map;

/**
 * Administrative user management endpoints (/api/admin/users/*).
 */
public final class AdminUserController {

    private AdminUserController() {}

    /** GET /api/admin/users?search=&roleId=&status=&sortBy=&sortOrder=&page=1&size=20 */
    public static void listUsers(RequestContext ctx) {
        String search = ctx.queryParam("search");
        String status = ctx.queryParam("status");
        String sortBy = ctx.queryParam("sortBy");
        String sortOrder = ctx.queryParam("sortOrder");

        Integer roleId = null;
        try {
            String r = ctx.queryParam("roleId");
            if (r != null && !r.trim().isEmpty()) roleId = Integer.valueOf(Integer.parseInt(r));
        } catch (NumberFormatException ignored) {}

        int page = parseIntOr(ctx.queryParam("page"), 1);
        int size = parseIntOr(ctx.queryParam("size"), 20);
        ResponseHelper.sendOk(ctx, UserService.listUsers(search, roleId, status, sortBy, sortOrder, page, size));
    }

    /** POST /api/admin/users */
    public static void createUser(RequestContext ctx) {
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            UserService.CreateUserResult res = UserService.createUser(
                    JsonHelper.str(body, "username"),
                    JsonHelper.str(body, "nickname"),
                    JsonHelper.str(body, "email"),
                    JsonHelper.str(body, "password"),
                    JsonHelper.intVal(body, "roleId", PermissionService.USER_ROLE_ID),
                    JsonHelper.str(body, "status"),
                    JsonHelper.boolVal(body, "mustChangePassword", false),
                    JsonHelper.boolVal(body, "require2fa", false)
            );
            if (res.success()) {
                ResponseHelper.sendOk(ctx, res.user());
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, res.errorCode(), res.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** PATCH /api/admin/users/{id} */
    public static void updateUser(RequestContext ctx) {
        Integer requesterId = (Integer) ctx.getAttribute("userId");
        int targetId;
        try {
            targetId = Integer.parseInt(ctx.pathParam("id"));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_ID", "Invalid user ID");
            return;
        }
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            UserService.UpdateUserResult res = UserService.updateUser(
                    targetId,
                    requesterId != null ? requesterId.intValue() : -1,
                    JsonHelper.intObj(body, "roleId"),
                    JsonHelper.str(body, "status"),
                    JsonHelper.str(body, "nickname"),
                    JsonHelper.str(body, "email"),
                    JsonHelper.str(body, "newPassword"),
                    JsonHelper.boolObj(body, "mustChangePassword"),
                    JsonHelper.boolObj(body, "require2fa")
            );
            if (res.success()) {
                ResponseHelper.sendOk(ctx, res.user());
            } else {
                HttpResponseStatus code = "NOT_FOUND".equals(res.errorCode())
                        ? HttpResponseStatus.NOT_FOUND
                        : HttpResponseStatus.BAD_REQUEST;
                ResponseHelper.sendError(ctx, code, res.errorCode(), res.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** DELETE /api/admin/users/{id} */
    public static void deleteUser(RequestContext ctx) {
        Integer requesterId = (Integer) ctx.getAttribute("userId");
        int targetId;
        try {
            targetId = Integer.parseInt(ctx.pathParam("id"));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_ID", "Invalid user ID");
            return;
        }
        UserService.DeleteResult res = UserService.deleteUser(targetId, requesterId != null ? requesterId.intValue() : -1);
        switch (res) {
            case SUCCESS:
                ResponseHelper.sendOk(ctx, Collections.singletonMap("message", "User deleted"));
                break;
            case NOT_FOUND:
                ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "NOT_FOUND", "User not found");
                break;
            case CANNOT_DELETE_SELF:
                ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "CANNOT_DELETE_SELF", "Cannot delete your own account");
                break;
            case LAST_ADMIN_PROTECTED:
                ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "LAST_ADMIN_PROTECTED", "Cannot delete the last active administrator");
                break;
        }
    }

    /** POST /api/admin/users/{id}/unbind */
    public static void unbindPlayer(RequestContext ctx) {
        int targetId;
        try {
            targetId = Integer.parseInt(ctx.pathParam("id"));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_ID", "Invalid user ID");
            return;
        }
        if (BindingService.unbindByUserId(targetId)) {
            ResponseHelper.sendOk(ctx, Collections.singletonMap("message", "Player unbound"));
        } else {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "NOT_BOUND", "User has no bound player");
        }
    }

    /** POST /api/admin/users/{id}/reset-2fa */
    public static void reset2fa(RequestContext ctx) {
        int targetId;
        try {
            targetId = Integer.parseInt(ctx.pathParam("id"));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_ID", "Invalid user ID");
            return;
        }
        if (UserService.resetUser2fa(targetId)) {
            ResponseHelper.sendOk(ctx, Collections.singletonMap("message", "Two-factor authentication cleared"));
        } else {
            ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "NOT_FOUND", "User not found");
        }
    }

    /** POST /api/admin/users/{id}/unlock */
    public static void unlockUser(RequestContext ctx) {
        int targetId;
        try {
            targetId = Integer.parseInt(ctx.pathParam("id"));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_ID", "Invalid user ID");
            return;
        }
        if (UserService.unlockUser(targetId)) {
            ResponseHelper.sendOk(ctx, Collections.singletonMap("message", "Account unlocked"));
        } else {
            ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "NOT_FOUND", "User not found");
        }
    }

    private static int parseIntOr(String raw, int fallback) {
        if (raw == null || raw.trim().isEmpty()) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

