package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.ProfilerService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Collections;

/**
 * REST endpoints for Server Performance Analysis &amp; Lag Diagnostics.
 */
public final class ProfilerController {

    private ProfilerController() {}

    /** GET /api/profiler/snapshot?refresh=true */
    public static void getSnapshot(RequestContext ctx) {
        boolean force = "true".equalsIgnoreCase(ctx.queryParam("refresh"));
        ResponseHelper.sendOk(ctx, ProfilerService.getProfilerSnapshot(force));
    }

    /** POST /api/profiler/teleport */
    public static void teleport(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        JsonObject body = JsonHelper.parseObject(ctx.body());
        String dim = JsonHelper.str(body, "dimension");
        if (dim == null || dim.isEmpty()) dim = "minecraft:overworld";
        double x = body.has("x") && !body.get("x").isJsonNull() ? body.get("x").getAsDouble() : 0.0;
        double y = body.has("y") && !body.get("y").isJsonNull() ? body.get("y").getAsDouble() : 80.0;
        double z = body.has("z") && !body.get("z").isJsonNull() ? body.get("z").getAsDouble() : 0.0;

        boolean ok = ProfilerService.teleportBoundAdmin(userId.intValue(), dim, x, y, z);
        if (!ok) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "TELEPORT_FAILED",
                    "Teleport requires an online bound Minecraft player");
            return;
        }
        ResponseHelper.sendOk(ctx, Collections.singletonMap("teleported", Boolean.TRUE));
    }
}
