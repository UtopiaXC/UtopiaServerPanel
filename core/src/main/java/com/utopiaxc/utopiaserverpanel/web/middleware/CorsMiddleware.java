package com.utopiaxc.utopiaserverpanel.web.middleware;

import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;

/**
 * CORS middleware permitting REST methods including PATCH and setup/auth headers.
 */
public class CorsMiddleware implements Middleware {

    @Override
    public boolean handle(RequestContext ctx) {
        ctx.addResponseHeader("Access-Control-Allow-Origin", "*");
        ctx.addResponseHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        ctx.addResponseHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Setup-Token, X-MFA-Token, X-Locale");

        if ("OPTIONS".equalsIgnoreCase(ctx.method())) {
            ResponseHelper.sendNoContent(ctx);
            return false;
        }
        return true;
    }
}
