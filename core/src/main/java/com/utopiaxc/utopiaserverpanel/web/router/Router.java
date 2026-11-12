package com.utopiaxc.utopiaserverpanel.web.router;

import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Route registry with path parameter extraction and permission metadata.
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
    public Router patch(String path, Route.RouteHandler handler) {
        routes.add(new Route("PATCH", path, handler)); return this;
    }
    public Router delete(String path, Route.RouteHandler handler) {
        routes.add(new Route("DELETE", path, handler)); return this;
    }

    public Router get(String path, Route.RouteHandler handler, String requiredPermission) {
        routes.add(new Route("GET", path, handler, requiredPermission)); return this;
    }
    public Router post(String path, Route.RouteHandler handler, String requiredPermission) {
        routes.add(new Route("POST", path, handler, requiredPermission)); return this;
    }
    public Router put(String path, Route.RouteHandler handler, String requiredPermission) {
        routes.add(new Route("PUT", path, handler, requiredPermission)); return this;
    }
    public Router patch(String path, Route.RouteHandler handler, String requiredPermission) {
        routes.add(new Route("PATCH", path, handler, requiredPermission)); return this;
    }
    public Router delete(String path, Route.RouteHandler handler, String requiredPermission) {
        routes.add(new Route("DELETE", path, handler, requiredPermission)); return this;
    }

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
