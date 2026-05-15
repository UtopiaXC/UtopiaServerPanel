package com.utopiaxc.utopiaserverpanel.web.context;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;

/**
 * Utility class for sending HTTP responses through a {@link RequestContext}.
 */
public final class ResponseHelper {
    private static final Gson GSON = new Gson();

    private ResponseHelper() {}

    public static void sendJson(RequestContext ctx, HttpResponseStatus status, Object data) {
        String json = (data instanceof String s) ? s : GSON.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        applyHeaders(ctx, response);
        ctx.nettyCtx().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        ctx.markResponded();
    }

    public static void sendOk(RequestContext ctx, Object data) {
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("code", 200);
        wrapper.add("data", GSON.toJsonTree(data));
        sendJson(ctx, HttpResponseStatus.OK, wrapper);
    }

    public static void sendError(RequestContext ctx, HttpResponseStatus status, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", status.code());
        error.addProperty("message", message);
        sendJson(ctx, status, error);
    }

    public static void sendFile(RequestContext ctx, byte[] content, String contentType) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(content));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length);
        applyHeaders(ctx, response);
        ctx.nettyCtx().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        ctx.markResponded();
    }

    public static void sendNoContent(RequestContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
        applyHeaders(ctx, response);
        ctx.nettyCtx().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        ctx.markResponded();
    }

    public static void sendNotFound(RequestContext ctx) {
        sendError(ctx, HttpResponseStatus.NOT_FOUND, "Not Found");
    }

    private static void applyHeaders(RequestContext ctx, FullHttpResponse response) {
        ctx.responseHeaders().forEach((k, v) -> response.headers().set(k, v));
    }
}
