package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.AuthService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Map;

/**
 * HTTP controller for player binding endpoints.
 */
public final class BindingController {
    private static final Gson GSON = new Gson();

    private BindingController() {}

    /**
     * POST /api/binding/bind
     * Body: { "code": "" }
     * Requires authentication.
     */
    public static void bind(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "Authentication required");
            return;
        }

        try {
            JsonObject body = GSON.fromJson(ctx.body(), JsonObject.class);
            String code = getString(body, "code");

            if (code == null || code.isBlank()) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Binding code is required");
                return;
            }

            AuthService.BindResult result = AuthService.bindUser(userId, code.trim().toUpperCase());
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
     * POST /api/binding/unbind
     * Body (admin): { "userId": 5 }
     * Body (self): { }
     * Requires authentication.
     */
    public static void unbind(RequestContext ctx) {
        Integer currentUserId = (Integer) ctx.getAttribute("userId");
        if (currentUserId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "Authentication required");
            return;
        }

        try {
            JsonObject body;
            try {
                body = GSON.fromJson(ctx.body(), JsonObject.class);
            } catch (Exception e) {
                body = new JsonObject();
            }

            int targetUserId;
            if (body.has("userId") && !body.get("userId").isJsonNull()) {
                targetUserId = body.get("userId").getAsInt();
                // Only admins can unbind other users
                Integer roleId = (Integer) ctx.getAttribute("roleId");
                if (roleId == null || roleId != 1) {
                    ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "Only admins can unbind other users");
                    return;
                }
            } else {
                targetUserId = currentUserId;
            }

            if (AuthService.unbindUser(targetUserId)) {
                ResponseHelper.sendOk(ctx, Map.of("message", "Successfully unbound"));
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Failed to unbind");
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Unbind failed");
        }
    }

    private static String getString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }
}
