package com.utopiaxc.utopiaserverpanel.web.middleware;

import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates Bearer JWT or API Access Token ("usp_...") and attaches verified principal attributes.
 */
public class AuthMiddleware implements Middleware {

    private static final Set<String> WHITELIST_METHOD_PATHS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "GET/api/setup/status",
            "POST/api/setup/verify-token",
            "POST/api/setup/test-smtp",
            "POST/api/setup/complete",
            "GET/api/site",
            "GET/api/captcha/challenge",
            "POST/api/auth/login",
            "POST/api/auth/register",
            "POST/api/auth/activate",
            "POST/api/auth/refresh",
            "POST/api/auth/forgot-password",
            "POST/api/auth/reset-password",
            "POST/api/auth/forgot-2fa",
            "POST/api/auth/reset-2fa",
            "GET/api/auth/guest-permissions"
    )));

    private static final Set<String> AUTH_REQUIRED_PREFIXES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "/api/me",
            "/api/player/me",
            "/api/binding/",
            "/api/admin/"
    )));

    @Override
    public boolean handle(RequestContext ctx) {
        if (!ctx.path().startsWith("/api/")) {
            return true;
        }

        String methodPath = ctx.method().toUpperCase() + ctx.path();
        if (WHITELIST_METHOD_PATHS.contains(methodPath)) {
            return true;
        }

        String rawToken = extractToken(ctx);
        if (rawToken != null) {
            TokenAuthHelper.Principal principal = TokenAuthHelper.resolve(rawToken);
            if (principal != null) {
                ctx.setAttribute("userId", Integer.valueOf(principal.getUserId()));
                ctx.setAttribute("username", principal.getUsername());
                ctx.setAttribute("roleId", Integer.valueOf(principal.getRoleId()));
                ctx.setAttribute("sessionId", principal.getSessionId());
                ctx.setAttribute("authType", principal.getAuthType());
                ctx.setAttribute("apiScopes", principal.getScopes());
                return true;
            }
            if (isAuthRequired(ctx.path())) {
                ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid or expired token");
                return false;
            }
            return true;
        }

        if (isAuthRequired(ctx.path())) {
            if ("POST/api/me/2fa/setup".equals(methodPath)) {
                String mfaToken = ctx.header("X-MFA-Token");
                if (mfaToken != null && !mfaToken.trim().isEmpty()) {
                    return true;
                }
            }
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return false;
        }

        return true;
    }

    private String extractToken(RequestContext ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return null;
    }

    private boolean isAuthRequired(String path) {
        for (String prefix : AUTH_REQUIRED_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }
}
