package com.utopiaxc.utopiaserverpanel.web.middleware;

import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;

/**
 * Built-in CORS middleware.
 */
public class CorsMiddleware implements Middleware {

    @Override
    public boolean handle(RequestContext ctx) {
        ctx.addResponseHeader("Access-Control-Allow-Origin", "*");
        ctx.addResponseHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ctx.addResponseHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(ctx.method())) {
            ResponseHelper.sendNoContent(ctx);
            return false;
        }
        return true;
    }
}
