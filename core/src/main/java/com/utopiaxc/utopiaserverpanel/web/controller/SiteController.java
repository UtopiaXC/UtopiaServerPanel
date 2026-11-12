package com.utopiaxc.utopiaserverpanel.web.controller;

import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.SettingsService;

public final class SiteController {

    private SiteController() {}

    /** GET /api/site — public safe site configuration and guest permissions. */
    public static void getPublicSiteInfo(RequestContext ctx) {
        ResponseHelper.sendOk(ctx, SettingsService.getPublicSiteInfo());
    }
}

