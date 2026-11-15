package com.utopiaxc.utopiaserverpanel.web.controller;

import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.SettingsService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public final class SiteController {

    private SiteController() {}

    /** GET /api/site — public safe site configuration and guest permissions. */
    public static void getPublicSiteInfo(RequestContext ctx) {
        ResponseHelper.sendOk(ctx, SettingsService.getPublicSiteInfo());
    }

    /** GET /api/site/favicon and GET /favicon.ico — active custom favicon or default mod icon. */
    public static void getFavicon(RequestContext ctx) {
        String version = ctx.queryParam("v");
        boolean immutable = version != null && !version.trim().isEmpty() && !"default".equals(version.trim());
        ctx.addResponseHeader("Cache-Control", immutable ? "public, max-age=86400" : "no-cache, must-revalidate");

        SettingsService.FaviconPayload custom = SettingsService.getCustomFaviconPayload();
        if (custom != null) {
            ResponseHelper.sendFile(ctx, custom.getBytes(), custom.getContentType());
            return;
        }

        try (InputStream is = SiteController.class.getResourceAsStream("/web/favicon.png")) {
            if (is == null) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.NOT_FOUND, "NOT_FOUND", "Default favicon not found");
                return;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            ResponseHelper.sendFile(ctx, baos.toByteArray(), "image/png");
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Failed to load favicon");
        }
    }
}

