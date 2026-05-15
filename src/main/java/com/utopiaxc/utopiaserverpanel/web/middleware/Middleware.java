package com.utopiaxc.utopiaserverpanel.web.middleware;

import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;

/**
 * Middleware interface for the HTTP request pipeline.
 * <p>
 * Implementations can inspect/modify the request context and decide whether
 * to continue processing (return {@code true}) or short-circuit (return {@code false},
 * after sending a response).
 * </p>
 * <p>
 * Example use-cases: CORS, authentication, rate-limiting, logging.
 * </p>
 */
@FunctionalInterface
public interface Middleware {
    /**
     * Process the request.
     * @param ctx the request context
     * @return {@code true} to continue to the next middleware / route handler,
     *         {@code false} to stop the chain (middleware must have sent a response)
     */
    boolean handle(RequestContext ctx);
}
