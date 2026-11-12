package com.utopiaxc.utopiaserverpanel.web.controller;

import com.utopiaxc.utopiaserverpanel.auth.CaptchaService;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.service.SettingsService;

public final class CaptchaController {

    private CaptchaController() {}

    /** GET /api/captcha/challenge — generates offline ALTCHA SHA-256 PoW challenge. */
    public static void challenge(RequestContext ctx) {
        int difficulty = SettingsService.getCaptchaDifficulty();
        ResponseHelper.sendOk(ctx, CaptchaService.createChallenge(difficulty));
    }
}

