package com.utopiaxc.utopiaserverpanel.web.context;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Wraps a Netty FullHttpRequest into a convenient request context object.
 */
public class RequestContext {
    private final ChannelHandlerContext nettyCtx;
    private final FullHttpRequest request;
    private final String path;
    private final String method;
    private final Map<String, String> queryParams;
    private final Map<String, String> pathParams = new LinkedHashMap<>();
    private final Map<String, String> responseHeaders = new LinkedHashMap<>();
    private final Map<String, Object> attributes = new HashMap<>();
    private boolean responded = false;

    public RequestContext(ChannelHandlerContext nettyCtx, FullHttpRequest request) {
        this.nettyCtx = nettyCtx;
        this.request = request;
        this.method = request.method().name();

        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        this.path = decoder.path();

        Map<String, String> params = new HashMap<>();
        decoder.parameters().forEach((k, v) -> {
            if (!v.isEmpty()) params.put(k, v.get(0));
        });
        this.queryParams = Collections.unmodifiableMap(params);
    }

    public ChannelHandlerContext nettyCtx() { return nettyCtx; }
    public FullHttpRequest request() { return request; }
    public String path() { return path; }
    public String method() { return method; }
    public Map<String, String> queryParams() { return queryParams; }
    public Map<String, String> responseHeaders() { return responseHeaders; }

    public String body() {
        return request.content().toString(StandardCharsets.UTF_8);
    }

    public void addResponseHeader(String key, String value) {
        responseHeaders.put(key, value);
    }

    public void setAttribute(String key, Object value) { attributes.put(key, value); }
    public Object getAttribute(String key) { return attributes.get(key); }

    /** Set a path parameter (extracted from route matching). */
    public void setPathParam(String key, String value) { pathParams.put(key, value); }

    /** Get a path parameter by name. */
    public String pathParam(String key) { return pathParams.get(key); }

    /** Get a query parameter by name. */
    public String queryParam(String key) { return queryParams.get(key); }

    public boolean isResponded() { return responded; }
    public void markResponded() { responded = true; }
}
