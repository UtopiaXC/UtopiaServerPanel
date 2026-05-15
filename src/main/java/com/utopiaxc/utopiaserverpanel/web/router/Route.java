package com.utopiaxc.utopiaserverpanel.web.router;

import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;

/**
 * Represents a single route entry: HTTP method + path pattern → handler.
 */
public record Route(String method, String path, RouteHandler handler) {

    @FunctionalInterface
    public interface RouteHandler {
        void handle(RequestContext ctx) throws Exception;
    }
}
