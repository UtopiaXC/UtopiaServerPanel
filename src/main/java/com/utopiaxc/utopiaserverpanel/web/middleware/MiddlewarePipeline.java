package com.utopiaxc.utopiaserverpanel.web.middleware;

import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes a chain of {@link Middleware} instances in registration order.
 * If any middleware returns {@code false}, the chain stops immediately.
 */
public class MiddlewarePipeline {
    private static final MiddlewarePipeline INSTANCE = new MiddlewarePipeline();
    private final List<Middleware> middlewares = new ArrayList<>();

    public static MiddlewarePipeline getInstance() { return INSTANCE; }

    /** Register a middleware to the end of the pipeline. */
    public MiddlewarePipeline use(Middleware middleware) {
        middlewares.add(middleware);
        return this;
    }

    /**
     * Execute all middlewares in order.
     * @return {@code true} if all passed, {@code false} if any short-circuited
     */
    public boolean execute(RequestContext ctx) {
        for (Middleware mw : middlewares) {
            if (!mw.handle(ctx)) return false;
        }
        return true;
    }

    /** Remove all registered middlewares. */
    public void clear() {
        middlewares.clear();
    }
}
