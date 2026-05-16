package com.utopiaxc.utopiaserverpanel.web.handler;

import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.middleware.MiddlewarePipeline;
import com.utopiaxc.utopiaserverpanel.web.router.Route;
import com.utopiaxc.utopiaserverpanel.web.router.Router;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Netty handler for HTTP requests (non-WebSocket).
 * Flow: Middleware → Route match & path params → Permission check → Static files → 404.
 */
public class HttpRequestDispatcher extends SimpleChannelInboundHandler<FullHttpRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        request.retain();
        try {
            RequestContext reqCtx = new RequestContext(ctx, request);

            // 1. Middleware pipeline (CORS → Auth)
            if (!MiddlewarePipeline.getInstance().execute(reqCtx)) {
                return;
            }

            // 2. API route matching with path param extraction
            Route matched = Router.getInstance().matchAndPopulate(reqCtx.method(), reqCtx.path(), reqCtx);
            if (matched != null) {
                // 3. Permission check
                String requiredPerm = matched.requiredPermission();
                if (requiredPerm != null) {
                    Integer userId = (Integer) reqCtx.getAttribute("userId");
                    if (userId == null) {
                        ResponseHelper.sendError(reqCtx, HttpResponseStatus.UNAUTHORIZED, "Authentication required");
                        return;
                    }
                    if (!PermissionService.hasPermission(userId, requiredPerm)) {
                        ResponseHelper.sendError(reqCtx, HttpResponseStatus.FORBIDDEN,
                                "Permission denied: " + requiredPerm);
                        return;
                    }
                }

                try {
                    matched.handler().handle(reqCtx);
                } catch (Exception e) {
                    UtopiaServerPanel.LOGGER.error("Route handler error for {} {}", reqCtx.method(), reqCtx.path(), e);
                    if (!reqCtx.isResponded()) {
                        ResponseHelper.sendError(reqCtx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");
                    }
                }
                return;
            }

            // 4. Static file fallback
            if (StaticFileHandler.tryServe(reqCtx)) {
                return;
            }

            // 5. 404
            ResponseHelper.sendNotFound(reqCtx);
        } finally {
            request.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof java.io.IOException) {
            UtopiaServerPanel.LOGGER.debug("Client connection reset: {}", cause.getMessage());
        } else {
            UtopiaServerPanel.LOGGER.error("HTTP dispatcher error", cause);
        }
        ctx.close();
    }
}
