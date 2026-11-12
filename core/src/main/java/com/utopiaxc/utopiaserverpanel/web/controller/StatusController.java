package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.StatusService;

/**
 * HTTP API controller for server status endpoints.
 */
public final class StatusController {

    private StatusController() {}

    /** GET /api/status */
    public static void getStatus(RequestContext ctx) {
        JsonObject statusObj = StatusService.getStatusObject();
        ResponseHelper.sendOk(ctx, statusObj);
    }
}

