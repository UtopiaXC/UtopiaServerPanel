package com.utopiaxc.utopiaserverpanel.web.handler;

import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
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
 * Netty HTTP request dispatcher: Middleware -> Route matching -> Level permission check -> Static SPA fallback -> 404.
 */
public class HttpRequestDispatcher extends SimpleChannelInboundHandler<FullHttpRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        request.retain();
        try {
            RequestContext reqCtx = new RequestContext(ctx, request);

            if (!MiddlewarePipeline.getInstance().execute(reqCtx)) {
                return;
            }

            Route matched = Router.getInstance().matchAndPopulate(reqCtx.method(), reqCtx.path(), reqCtx);
            if (matched != null) {
                String requiredPerm = matched.requiredPermission();
                if (requiredPerm != null) {
                    Integer userId = (Integer) reqCtx.getAttribute("userId");
                    if (userId == null) {
                        if (!PermissionService.guestMeetsRequirement(requiredPerm)) {
                            ResponseHelper.sendError(reqCtx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
                            return;
                        }
                    } else if (!PermissionService.meetsRequirement(userId, requiredPerm)) {
                        ResponseHelper.sendError(reqCtx, HttpResponseStatus.FORBIDDEN, "FORBIDDEN", "Permission denied: " + requiredPerm);
                        return;
                    }
                }

                try {
                    matched.handler().handle(reqCtx);
                } catch (Exception e) {
                    PanelLog.get().error("Route handler error for " + reqCtx.method() + " " + reqCtx.path(), e);
                    if (!reqCtx.isResponded()) {
                        ResponseHelper.sendError(reqCtx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal Server Error");
                    }
                }
                return;
            }

            if (reqCtx.path().startsWith("/api/")) {
                ResponseHelper.sendNotFound(reqCtx);
                return;
            }

            if (StaticFileHandler.tryServe(reqCtx)) {
                return;
            }

            ResponseHelper.sendNotFound(reqCtx);
        } finally {
            request.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof java.io.IOException) {
            PanelLog.get().debug("Client connection reset: {}", cause.getMessage());
        } else {
            PanelLog.get().error("HTTP dispatcher error", cause);
        }
        ctx.close();
    }
}
