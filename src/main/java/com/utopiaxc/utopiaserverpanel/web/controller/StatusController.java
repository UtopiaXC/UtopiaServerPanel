package com.utopiaxc.utopiaserverpanel.web.controller;

import com.utopiaxc.utopiaserverpanel.web.WebServer;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * HTTP API controller for server status endpoints.
 */
public final class StatusController {

    private StatusController() {}

    /** GET /api/status */
    public static void getStatus(RequestContext ctx) {
        var statusObj = com.utopiaxc.utopiaserverpanel.web.service.StatusService.getStatusObject(
                WebServer.getMinecraftServer(), WebServer.getStartTime());
        ResponseHelper.sendJson(ctx, HttpResponseStatus.OK, statusObj);
    }
}
