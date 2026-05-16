package com.utopiaxc.utopiaserverpanel.web.router;

import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Route registry with path parameter support and permission metadata.
 * Routes are matched in registration order; first match wins.
 */
public class Router {
    private static final Router INSTANCE = new Router();
    private final List<Route> routes = new ArrayList<>();

    public static Router getInstance() { return INSTANCE; }

    public Router get(String path, Route.RouteHandler handler) {
        routes.add(new Route("GET", path, handler)); return this;
    }
    public Router post(String path, Route.RouteHandler handler) {
        routes.add(new Route("POST", path, handler)); return this;
    }
    public Router put(String path, Route.RouteHandler handler) {
        routes.add(new Route("PUT", path, handler)); return this;
    }
    public Router delete(String path, Route.RouteHandler handler) {
        routes.add(new Route("DELETE", path, handler)); return this;
    }

    // ── With required permission ──

    public Router get(String path, Route.RouteHandler handler, String requiredPermission) {
        routes.add(new Route("GET", path, handler, requiredPermission)); return this;
    }
    public Router post(String path, Route.RouteHandler handler, String requiredPermission) {
        routes.add(new Route("POST", path, handler, requiredPermission)); return this;
    }
    public Router put(String path, Route.RouteHandler handler, String requiredPermission) {
        routes.add(new Route("PUT", path, handler, requiredPermission)); return this;
    }
    public Router delete(String path, Route.RouteHandler handler, String requiredPermission) {
        routes.add(new Route("DELETE", path, handler, requiredPermission)); return this;
    }

    /**
     * Find the first matching route. Populates path params on the RequestContext.
     */
    public Route matchAndPopulate(String method, String path, RequestContext ctx) {
        for (Route route : routes) {
            Route.MatchResult result = route.match(method, path);
            if (result != null) {
                result.pathParams().forEach(ctx::setPathParam);
                return result.route();
            }
        }
        return null;
    }

    public void clear() { routes.clear(); }
}
