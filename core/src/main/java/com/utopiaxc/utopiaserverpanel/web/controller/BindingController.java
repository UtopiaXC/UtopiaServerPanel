package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.BindingService;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BindingController {

    private BindingController() {}

    /** POST /api/binding/bind */
    public static void bind(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            String code = JsonHelper.str(body, "code");
            BindingService.BindResult res = BindingService.bindUserWithCode(userId.intValue(), code);
            if (res.success()) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("message", res.message());
                out.put("playerName", res.playerName());
                out.put("playerUuid", res.playerUuid());
                ResponseHelper.sendOk(ctx, out);
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, res.errorCode(), res.message());
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }

    /** POST /api/binding/unbind */
    public static void unbind(RequestContext ctx) {
        Integer currentUserId = (Integer) ctx.getAttribute("userId");
        if (currentUserId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        try {
            JsonObject body = JsonHelper.parseObject(ctx.body());
            Integer requestedTarget = JsonHelper.intObj(body, "userId");
            int targetUserId = currentUserId.intValue();
            if (requestedTarget != null && requestedTarget.intValue() != currentUserId.intValue()) {
                if (!PermissionService.meetsRequirement(currentUserId.intValue(), "admin:2")) {
                    ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "FORBIDDEN", "Permission denied");
                    return;
                }
                targetUserId = requestedTarget.intValue();
            }
            if (BindingService.unbindByUserId(targetUserId)) {
                ResponseHelper.sendOk(ctx, Collections.singletonMap("message", "Unbound successfully"));
            } else {
                ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "NOT_BOUND", "User is not bound to a player");
            }
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body");
        }
    }
}
