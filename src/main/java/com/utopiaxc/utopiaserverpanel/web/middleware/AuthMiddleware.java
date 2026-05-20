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
 * Routes are divided into three categories:
 * <ol>
 *   <li><strong>Whitelisted</strong>: Always pass through, no token extraction.</li>
 *   <li><strong>Auth-required</strong>: Endpoints listed in {@code AUTH_REQUIRED_PREFIXES} 
 *       that MUST have a valid token. Returns 401 if missing/invalid.</li>
 *   <li><strong>Soft-auth</strong>: All other /api/ routes. Token is extracted and attached
 *       if present, but missing tokens are allowed through. Permission checks happen
 *       at the route level in HttpRequestDispatcher (using guest role for anonymous).</li>
 * </ol>
 */
public class AuthMiddleware implements Middleware {

    /** Paths that never need authentication at all. */
    private static final Set<String> WHITELIST_METHOD_PATHS = Set.of(
            "POST/api/auth/login",
            "POST/api/auth/refresh",
            "POST/api/auth/logout",
            "POST/api/auth/register",
            "GET/api/auth/guest-permissions",
            "GET/api/status"
    );

    /** API path prefixes that strictly require authentication (return 401 if no token). */
    private static final Set<String> AUTH_REQUIRED_PREFIXES = Set.of(
            "/api/auth/me",
            "/api/auth/permissions",
            "/api/auth/change-password",
            "/api/auth/username",
            "/api/player/me",
            "/api/binding/"
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

        // Extract Bearer token from Authorization header (if present)
        String authHeader = ctx.request().headers().get("Authorization");
        boolean hasToken = authHeader != null && authHeader.startsWith("Bearer ");

        if (hasToken) {
            String token = authHeader.substring(7);
            Claims claims = JwtUtil.validateToken(token);
            if (claims != null) {
                // Valid token: attach user info to request context
                int userId = JwtUtil.getUserId(claims);
                String username = JwtUtil.getUsername(claims);
                int roleId = JwtUtil.getRoleId(claims);

                ctx.setAttribute("userId", userId);
                ctx.setAttribute("username", username);
                ctx.setAttribute("roleId", roleId);

                UtopiaServerPanel.LOGGER.debug("Authenticated user: {} (id={}, role={})", username, userId, roleId);
                return true;
            } else {
                // Invalid token provided: for auth-required paths, reject immediately
                if (isAuthRequired(ctx.path())) {
                    ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "Invalid or expired token");
                    return false;
                }
                // For other paths, continue without user info (guest)
                return true;
            }
        }

        // No token at all
        if (isAuthRequired(ctx.path())) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "Authentication required");
            return false;
        }

        // Allow through as anonymous/guest -- permission check happens at route level
        return true;
    }

    private boolean isAuthRequired(String path) {
        for (String prefix : AUTH_REQUIRED_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }
}
