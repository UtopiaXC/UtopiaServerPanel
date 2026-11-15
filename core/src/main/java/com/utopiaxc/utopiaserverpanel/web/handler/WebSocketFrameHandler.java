package com.utopiaxc.utopiaserverpanel.web.handler;

import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
import com.utopiaxc.utopiaserverpanel.web.controller.WebSocketController;
import com.utopiaxc.utopiaserverpanel.web.middleware.TokenAuthHelper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.util.List;

/**
 * Handles WebSocket frames and tracks per-channel authenticated principal.
 */
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    public static final AttributeKey<Integer> USER_ID_ATTR = AttributeKey.valueOf("usp_ws_user_id");
    public static final AttributeKey<String> AUTH_TOKEN_ATTR = AttributeKey.valueOf("usp_ws_auth_token");
    public static final AttributeKey<String> API_SCOPES_ATTR = AttributeKey.valueOf("usp_ws_api_scopes");
    private static final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    public static ChannelGroup getChannels() { return channels; }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete hc = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            QueryStringDecoder decoder = new QueryStringDecoder(hc.requestUri());
            List<String> tokens = decoder.parameters().get("token");
            if (tokens != null && !tokens.isEmpty()) {
                String raw = tokens.get(0);
                TokenAuthHelper.Principal principal = TokenAuthHelper.resolve(raw);
                if (principal != null) {
                    ctx.channel().attr(USER_ID_ATTR).set(Integer.valueOf(principal.getUserId()));
                    ctx.channel().attr(AUTH_TOKEN_ATTR).set(raw.trim());
                    ctx.channel().attr(API_SCOPES_ATTR).set("api_token".equals(principal.getAuthType()) ? principal.getScopes() : "*");
                }
            }
            channels.add(ctx.channel());
            WebSocketController.onConnect(ctx);
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        WebSocketController.onMessage(ctx, frame.text());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        channels.remove(ctx.channel());
        WebSocketController.onDisconnect(ctx);
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof java.io.IOException) {
            PanelLog.get().debug("WS client disconnected: {}", cause.getMessage());
        } else {
            PanelLog.get().error("WebSocket handler error", cause);
        }
        ctx.close();
    }
}
