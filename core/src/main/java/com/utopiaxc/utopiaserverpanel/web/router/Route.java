package com.utopiaxc.utopiaserverpanel.web.router;

import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A route entry: HTTP method + path pattern → handler + optional required permission.
 * Path patterns support {param} placeholders, e.g. /api/admin/users/{id}.
 */
public class Route {
    private final String method;
    private final String path;
    private final RouteHandler handler;
    private final String requiredPermission;

    public Route(String method, String path, RouteHandler handler) {
        this(method, path, handler, null);
    }

    public Route(String method, String path, RouteHandler handler, String requiredPermission) {
        this.method = method;
        this.path = path;
        this.handler = handler;
        this.requiredPermission = requiredPermission;
    }

    public String method() { return method; }
    public String path() { return path; }
    public RouteHandler handler() { return handler; }
    public String requiredPermission() { return requiredPermission; }

    /**
     * Try to match this route against a method and request path.
     * @return MatchResult with extracted path params, or null if no match.
     */
    public MatchResult match(String method, String requestPath) {
        if (!this.method.equalsIgnoreCase(method)) return null;

        String[] patternParts = this.path.split("/");
        String[] actualParts = requestPath.split("/");

        if (patternParts.length != actualParts.length) return null;

        Map<String, String> pathParams = new LinkedHashMap<>();

        for (int i = 0; i < patternParts.length; i++) {
            String pp = patternParts[i];
            String ap = actualParts[i];
            if (pp.startsWith("{") && pp.endsWith("}")) {
                pathParams.put(pp.substring(1, pp.length() - 1), ap);
            } else if (!pp.equals(ap)) {
                return null;
            }
        }
        return new MatchResult(this, pathParams);
    }

    public static final class MatchResult {
        private final Route route;
        private final Map<String, String> pathParams;

        public MatchResult(Route route, Map<String, String> pathParams) {
            this.route = route;
            this.pathParams = pathParams;
        }

        public Route route() { return route; }
        public Map<String, String> pathParams() { return pathParams; }
    }

    @FunctionalInterface
    public interface RouteHandler {
        void handle(RequestContext ctx) throws Exception;
    }
}
