package com.utopiaxc.utopiaserverpanel.web.controller;

import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.McSkinService;
import com.utopiaxc.utopiaserverpanel.web.service.ProfileService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Map;

/**
 * Public unauthenticated endpoints for viewing user player homepages (/{username})
 * and resolving same-origin Minecraft skin textures for canvas rendering.
 */
public final class PublicProfileController {

    private PublicProfileController() {}

    /** GET /api/public/users/{username} */
    public static void getUserProfile(RequestContext ctx) {
        String username = ctx.pathParam("username");
        Integer viewerUserId = (Integer) ctx.getAttribute("userId");
        Map<String, Object> profile = ProfileService.getPublicProfile(username, viewerUserId);
        if (profile == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "NOT_FOUND", "Player profile not found");
            return;
        }
        ResponseHelper.sendOk(ctx, profile);
    }

    /** GET /api/public/mc-skin?uuid=...&name=... */
    public static void getMcSkin(RequestContext ctx) {
        String uuid = ctx.queryParam("uuid");
        String name = ctx.queryParam("name");
        McSkinService.SkinPayload skin = McSkinService.resolveSkin(uuid, name);
        if (skin == null || skin.getPngBytes() == null) {
            ctx.addResponseHeader("Cache-Control", "public, max-age=180");
            ResponseHelper.sendNoContent(ctx);
            return;
        }
        ctx.addResponseHeader("Cache-Control", "public, max-age=900");
        ctx.addResponseHeader("X-Skin-Model", skin.isSlim() ? "slim" : "classic");
        ResponseHelper.sendFile(ctx, skin.getPngBytes(), "image/png");
    }
}
