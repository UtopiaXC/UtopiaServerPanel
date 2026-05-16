package com.utopiaxc.utopiaserverpanel.web.middleware;

import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import com.utopiaxc.utopiaserverpanel.auth.JwtUtil;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import io.jsonwebtoken.Claims;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Set;

/**
 * Authentication middleware that validates JWT tokens and attaches user info.
 * <p>
 * Whitelisted paths (no auth required):
 * <ul>
 *   <li>POST /api/auth/login</li>
 *   <li>POST /api/auth/refresh</li>
 *   <li>POST /api/auth/register</li>
 *   <li>Static file requests</li>
 *   <li>WebSocket upgrade requests</li>
 * </ul>
 * </p>
 */
public class AuthMiddleware implements Middleware {

    private static final Set<String> WHITELIST_METHOD_PATHS = Set.of(
            "POST/api/auth/login",
            "POST/api/auth/refresh",
            "POST/api/auth/logout",
            "POST/api/auth/register",
            "GET/api/status"
    );

    @Override
    public boolean handle(RequestContext ctx) {
        // Check if this path+method is whitelisted
        String key = ctx.method().toUpperCase() + ctx.path();
        if (WHITELIST_METHOD_PATHS.contains(key)) {
            return true;
        }

        // Allow static file paths (no /api/ prefix) and WebSocket upgrade
        if (!ctx.path().startsWith("/api/")) {
            return true;
        }

        // Extract Bearer token from Authorization header
        String authHeader = ctx.request().headers().get("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "Authentication required");
            return false;
        }

        String token = authHeader.substring(7);
        Claims claims = JwtUtil.validateToken(token);
        if (claims == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "Invalid or expired token");
            return false;
        }

        // Attach user info to request context
        int userId = JwtUtil.getUserId(claims);
        String username = JwtUtil.getUsername(claims);
        int roleId = JwtUtil.getRoleId(claims);

        ctx.setAttribute("userId", userId);
        ctx.setAttribute("username", username);
        ctx.setAttribute("roleId", roleId);

        UtopiaServerPanel.LOGGER.debug("Authenticated user: {} (id={}, role={})", username, userId, roleId);
        return true;
    }
}
