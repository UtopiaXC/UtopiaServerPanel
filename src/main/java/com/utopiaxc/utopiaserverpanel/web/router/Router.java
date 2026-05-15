package com.utopiaxc.utopiaserverpanel.web.router;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple route registry and matcher.
 * Routes are matched in registration order; first match wins.
 */
public class Router {
    private static final Router INSTANCE = new Router();
    private final List<Route> routes = new ArrayList<>();

    public static Router getInstance() { return INSTANCE; }

    public Router get(String path, Route.RouteHandler handler) {
        routes.add(new Route("GET", path, handler));
        return this;
    }

    public Router post(String path, Route.RouteHandler handler) {
        routes.add(new Route("POST", path, handler));
        return this;
    }

    public Router put(String path, Route.RouteHandler handler) {
        routes.add(new Route("PUT", path, handler));
        return this;
    }

    public Router delete(String path, Route.RouteHandler handler) {
        routes.add(new Route("DELETE", path, handler));
        return this;
    }

    /**
     * Find the first matching route for the given method and path.
     * @return the handler, or null if no match
     */
    public Route.RouteHandler match(String method, String path) {
        for (Route route : routes) {
            if (route.method().equalsIgnoreCase(method) && route.path().equals(path)) {
                return route.handler();
            }
        }
        return null;
    }

    /** Remove all registered routes (useful for hot-reload scenarios). */
    public void clear() {
        routes.clear();
    }
}
