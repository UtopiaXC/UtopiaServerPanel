package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.PlayerService;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Controller for player-related data endpoints.
 * Only the currently authenticated user can access their own player data.
 */
public final class PlayerController {

    private PlayerController() {}

    /**
     * GET /api/player/me - Get the authenticated user's bound player data.
     */
    public static void getMyPlayerData(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "Authentication required");
            return;
        }

        JsonObject playerData = PlayerService.getPlayerDataForUser(userId);
        if (playerData == null) {
            JsonObject result = new JsonObject();
            result.addProperty("bound", false);
            ResponseHelper.sendOk(ctx, result);
            return;
        }

        playerData.addProperty("bound", true);
        ResponseHelper.sendOk(ctx, playerData);
    }
}
