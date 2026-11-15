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
                Integer userId = (Integer) reqCtx.getAttribute("userId");
                String requiredPerm = matched.requiredPermission();
                if (requiredPerm != null) {
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
                String authType = (String) reqCtx.getAttribute("authType");
                String apiScopes = (String) reqCtx.getAttribute("apiScopes");
                if (userId != null && "api_token".equals(authType)
                        && !isPatScopeAllowed(apiScopes, requiredPerm, reqCtx.method(), reqCtx.path())) {
                    ResponseHelper.sendError(reqCtx, HttpResponseStatus.FORBIDDEN, "INSUFFICIENT_SCOPE",
                            "API token scope does not permit this operation");
                    return;
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

    public static boolean isPatScopeAllowed(String scopes, String requiredPerm, String method, String path) {
        String trimmed = scopes != null ? scopes.trim() : "";
        if (trimmed.isEmpty()) return false;
        if ("*".equals(trimmed)) return true;

        // Sensitive account/credential/binding mutations always require full '*' scope
        if (path != null) {
            boolean isMutation = !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method);
            if (isMutation && (path.startsWith("/api/me/") || path.startsWith("/api/binding/"))) {
                return false;
            }
        }

        if (requiredPerm != null && !requiredPerm.isEmpty()) {
            return scopeTokensAllow(trimmed, requiredPerm);
        }

        // For routes without explicit requiredPermission, check domain prefixes on restricted PATs
        if (path != null) {
            if (path.startsWith("/api/chat/") || path.startsWith("/api/messages/")) {
                boolean isMutation = !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method);
                return scopeTokensAllow(trimmed, isMutation ? "chat:2" : "chat:1")
                        || scopeTokensAllow(trimmed, isMutation ? "messages:2" : "messages:1");
            }
            if (path.startsWith("/api/me") || path.startsWith("/api/binding/") || path.startsWith("/api/player/")) {
                return scopeTokensAllow(trimmed, "profile:1") || scopeTokensAllow(trimmed, "read");
            }
        }
        return false;
    }

    private static boolean scopeTokensAllow(String scopes, String requiredPerm) {
        String[] reqParts = requiredPerm.split(":");
        String reqMod = reqParts[0].trim();
        int reqLv = reqParts.length > 1 ? parseIntSafe(reqParts[1].trim(), 1) : 1;
        for (String raw : scopes.split(",")) {
            String tok = raw.trim();
            if (tok.isEmpty()) continue;
            if ("*".equals(tok)) return true;
            String[] p = tok.split(":");
            String tokMod = p[0].trim();
            if (reqMod.equalsIgnoreCase(tokMod)) {
                int patLv = p.length > 1 ? parseIntSafe(p[1].trim(), 2) : 2;
                if (patLv >= reqLv) return true;
            }
        }
        return false;
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
